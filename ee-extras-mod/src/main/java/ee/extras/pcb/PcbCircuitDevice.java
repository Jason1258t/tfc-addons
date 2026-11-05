package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.electrical_properties.CapacitorProperties;
import com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Схема платы в общей матрице EE. Узлы 0–3 доступны для подключения проводов. */
public final class PcbCircuitDevice extends SimpleElectricalDevice {
    private static final int TERMINALS = 4;
    private static final int MAX_NODES = 32;
    private static final int MAX_PARTS = 64;
    private static final double CONTACT = 0.01;
    private static final double OPEN = 1e9;
    private static final double CAPACITOR_ESR = 0.1;
    private static final double DIODE_SENSE = 0.01;
    private static final double OVERLOAD_TICKS = 40;

    public enum Rating {
        L1(48, 12, 0.5), L2(128, 48, 1), L3(230, 120, 2);

        public final double volts;
        public final double watts;
        public final double amps;

        Rating(double volts, double watts, double amps) {
            this.volts = volts;
            this.watts = watts;
            this.amps = amps;
        }
    }

    private record Circuit(List<Part> parts, int[] groups, ElectricalProperties[] reactive, boolean hasCapacitor) {
        Circuit(List<Part> parts) {
            this(List.copyOf(parts), terminalGroups(parts), reactiveParts(parts),
                    parts.stream().anyMatch(part -> part.kind == Kind.CAPACITOR));
        }

        private static ElectricalProperties[] reactiveParts(List<Part> parts) {
            ElectricalProperties[] properties = new ElectricalProperties[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                if (part.kind == Kind.CAPACITOR) {
                    var capacitor = new CapacitorProperties();
                    capacitor.capacitance = part.ohms / 1000;
                    properties[i] = capacitor;
                } else if (part.kind == Kind.DIODE) {
                    properties[i] = new DiodeProperties();
                }
            }
            return properties;
        }

        private static int[] terminalGroups(List<Part> parts) {
            int[] group = new int[MAX_NODES];
            for (int i = 0; i < group.length; i++) group[i] = i;
            for (Part part : parts) {
                join(group, part.a, part.b);
                if (part.kind.controlled()) join(group, part.coilA, part.coilB);
            }
            return group;
        }

        private static void join(int[] group, int a, int b) {
            int old = group[b];
            int replacement = group[a];
            for (int i = 0; i < group.length; i++) if (group[i] == old) group[i] = replacement;
        }
    }

    private Circuit collectedCircuit;
    private CircuitBuilder collectedBuilder;
    private volatile Circuit circuit = new Circuit(List.of());
    private long buttonUntil;
    private volatile boolean switched;
    private volatile boolean powered;
    private volatile long closedMask;
    private volatile long blownFuses;
    private volatile boolean pressed;
    private boolean toggleRequested;
    private double indicatorVoltage;
    private volatile boolean burnt;
    private Rating rating = Rating.L1;
    private double overload;
    private double measuredWatts;
    private Kind meterKind;
    private double meterValue;

    public PcbCircuitDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    public enum Kind {
        TRACE, RESISTOR, RELAY, BUTTON, SWITCH, LAMP, REDSTONE_IN, REDSTONE_OUT,
        CAPACITOR, DIODE, FUSE, VOLTMETER, AMMETER, TRANSISTOR;

        boolean controlled() {
            return this == RELAY || this == TRANSISTOR;
        }
    }

    public record Part(Kind kind, int a, int b, double ohms, int coilA, int coilB, double threshold,
                       boolean normallyClosed) {
        public Part(Kind kind, int a, int b, double ohms, int coilA, int coilB, double threshold) {
            this(kind, a, b, ohms, coilA, coilB, threshold, false);
        }

        boolean valid() {
            return kind != null && node(a) && node(b) && a != b
                    && Double.isFinite(ohms) && ohms >= CONTACT && ohms <= OPEN
                    && Double.isFinite(threshold) && threshold > 0 && threshold <= 10000
                    && (!kind.controlled() || node(coilA) && node(coilB) && coilA != coilB);
        }

        private static boolean node(int index) {
            return index >= 0 && index < MAX_NODES;
        }
    }

    public void setCircuit(List<Part> parts) {
        setCircuit(parts, Rating.L1);
    }

    public void setCircuit(List<Part> parts, Rating rating) {
        if (parts.size() > MAX_PARTS || parts.stream().anyMatch(part -> !part.valid())) {
            throw new IllegalArgumentException("Недопустимая схема платы");
        }
        closedMask = 0;
        blownFuses = 0;
        this.rating = rating;
        circuit = new Circuit(parts);
        deviceSD.setDirty();
    }

    public void interact(boolean toggle, long now) {
        if (burnt) return;
        if (toggle) {
            toggleRequested = !toggleRequested;
        } else {
            buttonUntil = now + 20;
        }
    }

    @Override
    public void preTick(BridgeCollector collector) {
        if (burnt) return;
        Circuit current = circuit;
        collectedCircuit = current;
        collectedBuilder = collector.getCircuitBuilder();
        var builder = collector.builder(pos);
        List<Part> parts = current.parts;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            int branch = MAX_NODES + i;
            double sense = senseResistance(part);
            builder.node(branch).resistor(branch, part.b, sense);
            if (current.reactive[i] != null) {
                builder.connect(part.a, branch, current.reactive[i]);
            } else {
                builder.resistor(part.a, branch, resistance(part, i) - sense);
            }
            if (part.kind.controlled()) {
                int coil = MAX_NODES + MAX_PARTS + i;
                double half = coilResistance(part) / 2;
                builder.node(coil).resistor(part.coilA, coil, half).resistor(coil, part.coilB, half);
            }
        }
    }

    private static double coilResistance(Part part) {
        return part.kind == Kind.RELAY ? part.ohms : OPEN;
    }

    private static double senseResistance(Part part) {
        return switch (part.kind) {
            case CAPACITOR -> CAPACITOR_ESR;
            case DIODE -> DIODE_SENSE;
            default -> CONTACT / 2;
        };
    }

    private double resistance(Part part, int index) {
        long bit = 1L << index;
        return switch (part.kind) {
            case TRACE, AMMETER -> CONTACT;
            case VOLTMETER, REDSTONE_OUT -> OPEN;
            case BUTTON -> pressed ? CONTACT : OPEN;
            case SWITCH -> switched ? CONTACT : OPEN;
            case REDSTONE_IN -> powered ? CONTACT : OPEN;
            case RELAY, TRANSISTOR -> ((closedMask & bit) != 0) != part.normallyClosed ? CONTACT : OPEN;
            case FUSE -> (blownFuses & bit) == 0 ? CONTACT : OPEN;
            default -> part.ohms;
        };
    }

    @Override
    public void postTick(SimulationResults results) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PcbCircuitBlock)) return;
        if (burnt || collectedBuilder != results.circuitBuilder || collectedCircuit != circuit) return;
        collectedBuilder = null;
        double watts = 0;
        double maxVoltage = 0;
        int[] groups = circuit.groups;
        for (int a = 0; a < TERMINALS; a++) {
            for (int b = a + 1; b < TERMINALS; b++) {
                if (groups[a] == groups[b]) {
                    maxVoltage = Math.max(maxVoltage, Math.sqrt(results.getVoltageAtSqr(pos, a, b)));
                }
            }
        }
        double maxCurrent = 0;
        Circuit current = circuit;
        long nextFuses = blownFuses;
        for (int i = 0; i < current.parts.size(); i++) {
            Part part = current.parts.get(i);
            double squared = results.getVoltageAtSqr(pos, part.a, part.b);
            maxVoltage = Math.max(maxVoltage, Math.sqrt(squared));
            double sense = senseResistance(part);
            double sensedSquared = results.getVoltageAtSqr(pos, MAX_NODES + i, part.b);
            double amps = Math.sqrt(sensedSquared) / sense;
            if (part.kind == Kind.CAPACITOR) {
                watts += sensedSquared / sense;
                maxVoltage = Math.max(maxVoltage, voltage(results, part.a, MAX_NODES + i));
            } else {
                watts += Math.sqrt(squared) * amps;
            }
            maxCurrent = Math.max(maxCurrent, amps);
            if (part.kind == Kind.FUSE && amps > part.threshold / 100) nextFuses |= 1L << i;
            if (part.kind.controlled()) {
                double coilSquared = results.getVoltageAtSqr(pos, part.coilA, part.coilB);
                watts += coilSquared / coilResistance(part);
                maxVoltage = Math.max(maxVoltage, Math.sqrt(coilSquared));
                maxCurrent = Math.max(maxCurrent, Math.sqrt(coilSquared) / coilResistance(part));
            }
        }
        if (nextFuses != blownFuses) {
            blownFuses = nextFuses;
            deviceSD.setDirty();
            if (level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity entity) entity.setBlownFuses(nextFuses);
        }
        measuredWatts = watts;
        double currentRatio = maxCurrent / rating.amps;
        double load = Math.max(watts / rating.watts, currentRatio * currentRatio);
        double nextOverload = Math.max(0, overload + load - 1);
        if (nextOverload != overload || current.hasCapacitor) deviceSD.setDirty();
        overload = nextOverload;
        if (maxVoltage > rating.volts + 1e-6 || overload >= OVERLOAD_TICKS) {
            burnt = true;
            deviceSD.setDirty();
            level.setBlockAndUpdate(pos, state.setValue(PcbCircuitBlock.BURNT, true)
                    .setValue(PcbCircuitBlock.LIT, false).setValue(PcbCircuitBlock.OUTPUT, 0));
            return;
        }
        pressed = level.getGameTime() < buttonUntil;
        if (toggleRequested) {
            switched = !switched;
            toggleRequested = false;
            deviceSD.setDirty();
        }
        powered = level.isLoaded(pos.north()) && level.getSignal(pos.north(), Direction.NORTH) > 0;
        boolean lit = false;
        double maxIndicatorVoltage = 0;
        int output = 0;
        long closed = 0;
        meterKind = null;
        meterValue = 0;
        for (int i = 0; i < current.parts.size(); i++) {
            Part part = current.parts.get(i);
            switch (part.kind) {
                case RELAY, TRANSISTOR -> {
                    long bit = 1L << i;
                    double threshold = (closedMask & bit) != 0 ? part.threshold * 0.8 : part.threshold;
                    double control = part.kind == Kind.RELAY ? voltage(results, part.coilA, part.coilB)
                            : signedVoltage(results, part.coilA, part.coilB);
                    if (control >= threshold) closed |= bit;
                }
                case LAMP -> {
                    double volts = voltage(results, part.a, part.b);
                    maxIndicatorVoltage = Math.max(maxIndicatorVoltage, volts);
                    lit |= volts >= part.threshold;
                }
                case REDSTONE_OUT -> {
                    if (voltage(results, part.a, part.b) >= part.threshold) output = 15;
                }
                case VOLTMETER, AMMETER -> {
                    boolean currentMeter = part.kind == Kind.AMMETER;
                    double value = voltage(results, part.a, part.b) / (currentMeter ? CONTACT : 1);
                    double scale = part.threshold / (currentMeter ? 100 : 1);
                    output = Math.max(output, (int) Math.clamp(Math.floor(15 * value / scale), 0, 15));
                    if (meterKind == null) {
                        meterKind = part.kind;
                        meterValue = value;
                    }
                }
                default -> { }
            }
        }
        closedMask = closed;
        indicatorVoltage = maxIndicatorVoltage;
        BlockState updated = state.setValue(PcbCircuitBlock.LIT, lit).setValue(PcbCircuitBlock.OUTPUT, output);
        if (updated != state) level.setBlockAndUpdate(pos, updated);
    }

    private double signedVoltage(SimulationResults results, int a, int b) {
        double[] first = results.getVoltages(new InWorldNode(a, pos));
        double[] second = results.getVoltages(new InWorldNode(b, pos));
        if (first.length == 0 || second.length == 0) return 0;
        return first[first.length - 1] - second[second.length - 1];
    }

    private double voltage(SimulationResults results, int a, int b) {
        return Math.sqrt(results.getVoltageAtSqr(pos, a, b));
    }

    void restoreFuses(long mask) {
        blownFuses = mask;
        deviceSD.setDirty();
    }

    Kind meterKind() {
        return burnt ? null : meterKind;
    }

    double meterValue() {
        return meterValue;
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public void read(CompoundTag tag) {
        switched = tag.getBoolean("Switch");
        burnt = tag.getBoolean("Burnt");
        blownFuses = tag.getLong("BlownFuses");
        overload = Math.clamp(tag.getDouble("Overload"), 0, OVERLOAD_TICKS);
        try {
            rating = Rating.valueOf(tag.getString("Rating"));
        } catch (IllegalArgumentException ignored) {
            rating = Rating.L1;
        }
        ListTag serialized = tag.getList("Circuit", Tag.TAG_COMPOUND);
        if (serialized.size() > MAX_PARTS) return;
        List<Part> parts = new ArrayList<>();
        for (Tag entry : serialized) {
            CompoundTag item = (CompoundTag) entry;
            try {
                Part part = new Part(Kind.valueOf(item.getString("Kind")), item.getInt("A"), item.getInt("B"),
                        item.getDouble("Ohms"), item.getInt("CoilA"), item.getInt("CoilB"), item.getDouble("Threshold"),
                        item.getBoolean("NormallyClosed"));
                if (!part.valid()) return;
                parts.add(part);
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }
        circuit = new Circuit(parts);
        ListTag charges = tag.getList("Charge", Tag.TAG_DOUBLE);
        for (int i = 0; i < Math.min(charges.size(), circuit.reactive.length); i++) {
            if (circuit.reactive[i] instanceof CapacitorProperties capacitor && charges.get(i) instanceof net.minecraft.nbt.DoubleTag charge)
                capacitor.lastVoltage = Math.clamp(charge.getAsDouble(), -rating.volts, rating.volts);
        }
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Switch", switched);
        tag.putBoolean("Burnt", burnt);
        tag.putLong("BlownFuses", blownFuses);
        tag.putDouble("Meter", meterValue);
        ListTag charges = new ListTag();
        for (ElectricalProperties reactive : circuit.reactive) {
            charges.add(net.minecraft.nbt.DoubleTag.valueOf(reactive instanceof CapacitorProperties capacitor
                    ? capacitor.lastVoltage : 0));
        }
        tag.put("Charge", charges);
        tag.putDouble("Overload", overload);
        tag.putString("Rating", rating.name());
        tag.putDouble("Watts", measuredWatts);
        tag.putDouble("IndicatorVoltage", indicatorVoltage);
        ListTag serialized = new ListTag();
        for (Part part : circuit.parts) {
            CompoundTag item = new CompoundTag();
            item.putString("Kind", part.kind.name());
            item.putInt("A", part.a);
            item.putInt("B", part.b);
            item.putDouble("Ohms", part.ohms);
            item.putInt("CoilA", part.coilA);
            item.putInt("CoilB", part.coilB);
            item.putDouble("Threshold", part.threshold);
            if (part.normallyClosed) item.putBoolean("NormallyClosed", true);
            serialized.add(item);
        }
        tag.put("Circuit", serialized);
    }
}
