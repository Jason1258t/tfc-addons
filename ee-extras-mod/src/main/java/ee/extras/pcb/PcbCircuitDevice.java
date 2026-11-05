package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
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

    private volatile List<Part> circuit = List.of();
    private long buttonUntil;
    private volatile boolean switched;
    private volatile boolean powered;
    private volatile long relayMask;
    private volatile boolean pressed;
    private boolean toggleRequested;
    private double indicatorVoltage;

    public PcbCircuitDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    public enum Kind { TRACE, RESISTOR, RELAY, BUTTON, SWITCH, LAMP, REDSTONE_IN, REDSTONE_OUT }

    public record Part(Kind kind, int a, int b, double ohms, int coilA, int coilB, double threshold) {
        boolean valid() {
            return kind != null && node(a) && node(b) && a != b
                    && Double.isFinite(ohms) && ohms >= CONTACT && ohms <= OPEN
                    && Double.isFinite(threshold) && threshold > 0 && threshold <= 10000
                    && (kind != Kind.RELAY || node(coilA) && node(coilB) && coilA != coilB);
        }

        private static boolean node(int index) {
            return index >= 0 && index < MAX_NODES;
        }
    }

    public void setCircuit(List<Part> parts) {
        if (parts.size() > MAX_PARTS || parts.stream().anyMatch(part -> !part.valid())) {
            throw new IllegalArgumentException("Недопустимая схема платы");
        }
        relayMask = 0;
        circuit = List.copyOf(parts);
        deviceSD.setDirty();
    }

    public void interact(boolean toggle, long now) {
        if (toggle) {
            toggleRequested = !toggleRequested;
        } else {
            buttonUntil = now + 20;
        }
    }

    @Override
    public void preTick(BridgeCollector collector) {
        var builder = collector.builder(pos);
        long relays = relayMask;
        boolean buttonClosed = pressed;
        boolean switchClosed = switched;
        boolean input = powered;
        List<Part> parts = circuit;
        int nodes = 0;
        for (Part part : parts) {
            nodes |= (1 << part.a) | (1 << part.b);
            if (part.kind == Kind.RELAY) nodes |= (1 << part.coilA) | (1 << part.coilB);
        }
        for (int node = TERMINALS; node < MAX_NODES; node++) {
            if ((nodes & (1 << node)) != 0) builder.node(node);
        }
        for (Part part : parts) {
            double resistance = switch (part.kind) {
                case TRACE -> CONTACT;
                case BUTTON -> buttonClosed ? CONTACT : OPEN;
                case SWITCH -> switchClosed ? CONTACT : OPEN;
                case REDSTONE_IN -> input ? CONTACT : OPEN;
                case RELAY -> {
                    builder.resistor(part.coilA, part.coilB, part.ohms);
                    boolean closed = (relays & 1) != 0;
                    relays >>>= 1;
                    yield closed ? CONTACT : OPEN;
                }
                default -> part.ohms;
            };
            builder.resistor(part.a, part.b, resistance);
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PcbCircuitBlock)) return;
        pressed = level.getGameTime() < buttonUntil;
        if (toggleRequested) {
            switched = !switched;
            toggleRequested = false;
            deviceSD.setDirty();
        }
        powered = level.getSignal(pos.north(), Direction.NORTH) > 0;
        boolean lit = false;
        double maxIndicatorVoltage = 0;
        boolean output = false;
        int relay = 0;
        long relays = 0;
        for (Part part : circuit) {
            switch (part.kind) {
                case RELAY -> {
                    double threshold = ((relayMask >>> relay) & 1) != 0 ? part.threshold * 0.8 : part.threshold;
                    if (voltage(results, part.coilA, part.coilB) >= threshold) relays |= 1L << relay;
                    relay++;
                }
                case LAMP -> {
                    double volts = voltage(results, part.a, part.b);
                    maxIndicatorVoltage = Math.max(maxIndicatorVoltage, volts);
                    lit |= volts >= part.threshold;
                }
                case REDSTONE_OUT -> output |= voltage(results, part.a, part.b) >= part.threshold;
                default -> { }
            }
        }
        relayMask = relays;
        indicatorVoltage = maxIndicatorVoltage;
        BlockState updated = state.setValue(PcbCircuitBlock.LIT, lit).setValue(PcbCircuitBlock.OUTPUT, output);
        if (updated != state) level.setBlockAndUpdate(pos, updated);
    }

    private double voltage(SimulationResults results, int a, int b) {
        return Math.abs(results.getVoltageAt(pos, a, b));
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public void read(CompoundTag tag) {
        switched = tag.getBoolean("Switch");
        ListTag serialized = tag.getList("Circuit", Tag.TAG_COMPOUND);
        if (serialized.size() > MAX_PARTS) return;
        List<Part> parts = new ArrayList<>();
        for (Tag entry : serialized) {
            CompoundTag item = (CompoundTag) entry;
            try {
                Part part = new Part(Kind.valueOf(item.getString("Kind")), item.getInt("A"), item.getInt("B"),
                        item.getDouble("Ohms"), item.getInt("CoilA"), item.getInt("CoilB"), item.getDouble("Threshold"));
                if (!part.valid()) return;
                parts.add(part);
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }
        circuit = List.copyOf(parts);
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Switch", switched);
        tag.putDouble("IndicatorVoltage", indicatorVoltage);
        ListTag serialized = new ListTag();
        for (Part part : circuit) {
            CompoundTag item = new CompoundTag();
            item.putString("Kind", part.kind.name());
            item.putInt("A", part.a);
            item.putInt("B", part.b);
            item.putDouble("Ohms", part.ohms);
            item.putInt("CoilA", part.coilA);
            item.putInt("CoilB", part.coilB);
            item.putDouble("Threshold", part.threshold);
            serialized.add(item);
        }
        tag.put("Circuit", serialized);
    }
}
