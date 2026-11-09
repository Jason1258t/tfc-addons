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

/** Схема платы в общей матрице EE. Внешние узлы нумеруются в порядке контактов на плате. */
public final class PcbCircuitDevice extends SimpleElectricalDevice {
    private static final int LEGACY_TERMINALS = 4;
    static final int MAX_TERMINALS = 4 * (PcbTopology.SIZE - 1);
    private static final int MAX_PARTS = 64;
    private static final int MAX_NODES = MAX_TERMINALS + PcbTopology.CELLS * PcbTopology.MAX_LAYERS;
    private static final double CONTACT = 0.01;
    private static final double OPEN = 1e9;
    private static final double CAPACITOR_ESR = 0.1;
    private static final double DIODE_SENSE = 0.01;
    private static final double OVERLOAD_TICKS = 40;
    private static final double INDICATOR_MIN_OHMS = 220;

    static double indicatorResistance(double ohms) {
        return Math.max(INDICATOR_MIN_OHMS, ohms);
    }

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

    private record Circuit(List<Part> parts, int terminals, int[] groups, int[] highVoltageGroups, ElectricalProperties[] reactive, boolean hasCapacitor) {
        Circuit(List<Part> parts, int terminals) {
            this(List.copyOf(parts), terminals, terminalGroups(parts), highVoltageGroups(parts), reactiveParts(parts),
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
                } else if (part.kind == Kind.DIODE || part.kind.led()) {
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
                // ВВ-вторичка изолирована внутри корпуса. Её полюса не объединяются в общий
                // низковольтный контур; подключённая обычная деталь проверяется по прежнему пределу.
                if (part.kind == Kind.BRIDGE_RECTIFIER || part.kind == Kind.VOLTAGE_REGULATOR) { join(group,part.a,part.coilA); join(group,part.a,part.coilB); }
                if(part.kind == Kind.OPTOCOUPLER) { join(group,part.coilA,part.coilB); join(group,part.coilA,part.alternate); }
                if(part.kind.sensor()) join(group,part.a,part.coilA);
                if (part.kind.controlled()) join(group, part.coilA, part.coilB);
                if (part.windingInput()) join(group, part.a, part.coilA);
                if (part.kind == Kind.LATCHING_RELAY) { join(group, part.coilA, part.alternate); join(group, part.coilB, part.alternate); }
                if (PcbLogic.chip(part.kind)) for (int pin : part.pins) join(group,part.a,pin);
                if (part.poweredLogic()) { join(group,part.a,part.coilA); join(group,part.a,part.logicOutput()); if (part.kind == Kind.COMPARATOR) join(group,part.a,part.coilB); }
                if (part.kind == Kind.POTENTIOMETER) join(group, part.a, part.coilA);
                if (part.changeover()) join(group, part.a, part.alternateNode());
            }
            return group;
        }

        private static int[] highVoltageGroups(List<Part> parts) {
            int[] groups=terminalGroups(parts);
            for (Part part:parts) if (part.kind == Kind.STEP_UP_TRANSFORMER) join(groups,part.coilA,part.coilB);
            return groups;
        }

        private static void join(int[] group, int a, int b) {
            int old = group[b];
            int replacement = group[a];
            for (int i = 0; i < group.length; i++) if (group[i] == old) group[i] = replacement;
        }
    }

    private final PcbOnboardTransformer onboardPower;
    private PcbOptocoupler[] optos = new PcbOptocoupler[MAX_PARTS];
    private PcbSensor[] sensors = new PcbSensor[MAX_PARTS];
    private PcbVoltageRegulator[] regulators = new PcbVoltageRegulator[MAX_PARTS];
    private PcbBridgeRectifier[] bridges = new PcbBridgeRectifier[MAX_PARTS];
    private PcbStepUpTransformer[] stepUps = new PcbStepUpTransformer[MAX_PARTS];
    private Circuit collectedCircuit;
    private CircuitBuilder collectedBuilder;
    private volatile Circuit circuit = new Circuit(List.of(), LEGACY_TERMINALS);
    private static int[] defaultPositions() {
        int[] values = new int[MAX_PARTS];
        java.util.Arrays.fill(values, 50);
        return values;
    }

    private volatile int[] logicWords = new int[MAX_PARTS], logicInputs = new int[MAX_PARTS];
    private volatile long logicHigh, logicPowered;
    private static final double LOGIC_DRIVER_OHMS = 100;
    private volatile long windingHigh;
    private volatile long[] timerDue = new long[MAX_PARTS];
    private long pausedClocks;
    private volatile int[] positions = defaultPositions();
    private long buttonUntil;
    private volatile boolean switched;
    private volatile long switches;
    private volatile long pressedComponents;
    private final long[] buttonExpiries = new long[MAX_PARTS];
    private volatile boolean powered;
    private volatile long closedMask;
    private volatile long latchedMask;
    private volatile double[] controlVoltages = new double[0];
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
        onboardPower = new PcbOnboardTransformer(pos);
    }

    public enum Kind {
        TRACE, RESISTOR, RELAY, BUTTON, SWITCH, LAMP, REDSTONE_IN, REDSTONE_OUT,
        CAPACITOR, DIODE, FUSE, VOLTMETER, AMMETER, TRANSISTOR, LED, RHEOSTAT, POTENTIOMETER, MECHANICAL_TIMER, CLOCK, LED_PANEL, LED_STRIP, LATCHING_RELAY, THREE_POSITION_SWITCH, COMPARATOR, D_FLIP_FLOP, COUNTER, REGISTER, DECODER, ONBOARD_TRANSFORMER, STEP_UP_TRANSFORMER, BRIDGE_RECTIFIER, VOLTAGE_REGULATOR, TEMPERATURE_SENSOR, LIGHT_SENSOR, ROTATION_SENSOR, OPTOCOUPLER;

        boolean sensor() { return this == TEMPERATURE_SENSOR || this == LIGHT_SENSOR || this == ROTATION_SENSOR; }

        boolean supply() { return this == ONBOARD_TRANSFORMER; }

        boolean led() { return this == LED || this == LED_PANEL || this == LED_STRIP; }

        boolean controlled() {
            return this == RELAY || this == TRANSISTOR;
        }
    }

    public record Part(Kind kind, int a, int b, double ohms, int coilA, int coilB, double threshold,
                       boolean normallyClosed, int moduleIndex, int moduleType, int alternate, int[] pins) {
        public Part { pins = pins == null ? new int[0] : pins.clone(); }
        @Override public int[] pins() { return pins.clone(); }
        public Part(Kind kind,int a,int b,double ohms,int coilA,int coilB,double threshold,boolean normallyClosed,int moduleIndex,int moduleType,int alternate) {
            this(kind,a,b,ohms,coilA,coilB,threshold,normallyClosed,moduleIndex,moduleType,alternate,new int[0]);
        }
        public Part(Kind kind, int a, int b, double ohms, int coilA, int coilB, double threshold, boolean normallyClosed, int moduleIndex, int moduleType) {
            this(kind, a, b, ohms, coilA, coilB, threshold, normallyClosed, moduleIndex, moduleType, -1);
        }

        public Part(Kind kind, int a, int b, double ohms, int coilA, int coilB, double threshold, boolean normallyClosed) {
            this(kind, a, b, ohms, coilA, coilB, threshold, normallyClosed, -1, -1);
        }

        public Part(Kind kind, int a, int b, double ohms, int coilA, int coilB, double threshold) {
            this(kind, a, b, ohms, coilA, coilB, threshold, false);
        }

        boolean poweredLogic() { return kind == Kind.COMPARATOR || kind == Kind.CLOCK; }
        int logicOutput() { return kind == Kind.COMPARATOR ? alternate : coilB; }
        boolean windingInput() { return kind == Kind.MECHANICAL_TIMER && moduleIndex >= 0; }
        boolean changeover() { return moduleIndex >= 0 && (kind == Kind.SWITCH || kind == Kind.THREE_POSITION_SWITCH || kind == Kind.RELAY && alternate >= 0); }
        int alternateNode() { return kind == Kind.SWITCH || kind == Kind.THREE_POSITION_SWITCH ? coilA : alternate; }

        boolean valid() {
            return kind != null
                    && (moduleIndex == -1 && moduleType == -1 || moduleIndex >= 0 && moduleIndex < PcbTopology.CELLS * PcbTopology.MAX_LAYERS
                            && moduleType >= 0 && moduleType < PcbModules.ALL.length && PcbModules.ALL[moduleType].kind() == kind)
                    && node(a) && node(b) && a != b
                    && (!PcbLogic.chip(kind) || pins.length == PcbLogic.pins(kind) && java.util.Arrays.stream(pins).allMatch(Part::node) && a == pins[pins.length-2] && b == pins[pins.length-1])
                    && Double.isFinite(ohms) && ohms >= CONTACT && ohms <= OPEN
                    && (!kind.supply() || threshold >= 1 && threshold <= 48)
                    && Double.isFinite(threshold) && threshold > 0 && threshold <= (kind == Kind.MECHANICAL_TIMER || kind == Kind.CLOCK ? 12000 : 10000)
                    && (kind != Kind.CLOCK && kind != Kind.MECHANICAL_TIMER || threshold >= 2 && threshold == Math.rint(threshold))
                    && (!windingInput() || node(coilA) && coilA != a && coilB == a)
                    && (!changeover() || node(alternateNode()) && alternateNode() != a && alternateNode() != b)
                    && (alternate == -1 || (kind == Kind.RELAY || kind == Kind.LATCHING_RELAY || kind == Kind.COMPARATOR || kind == Kind.OPTOCOUPLER) && node(alternate))
                    && (!poweredLogic() || node(coilA) && node(coilB) && node(logicOutput()))
                    && (kind != Kind.LATCHING_RELAY || node(coilA) && node(coilB) && node(alternate) && coilA != alternate && coilB != alternate)
                    && (kind != Kind.OPTOCOUPLER || node(coilA) && node(coilB) && node(alternate) && a != coilA && a != coilB && a != alternate && b != coilA && b != coilB && b != alternate)
                    && (!kind.sensor() || node(coilA))
                    && (kind != Kind.POTENTIOMETER || node(coilA) && coilA != a && coilA != b)
                    && (kind != Kind.BRIDGE_RECTIFIER && kind != Kind.VOLTAGE_REGULATOR || node(coilA) && node(coilB))
                    && (kind != Kind.STEP_UP_TRANSFORMER || node(coilA) && node(coilB) && coilA != coilB
                            && coilA != a && coilA != b && coilB != a && coilB != b)
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
        setCircuit(parts, rating, LEGACY_TERMINALS);
    }

    void setCircuit(List<Part> parts, Rating rating, int terminals) {
        if (parts.size() > MAX_PARTS || parts.stream().anyMatch(part -> !part.valid()) || parts.stream().filter(part -> part.kind.supply()).count() > 1) {
            throw new IllegalArgumentException("Недопустимая схема платы");
        }
        onboardPower.reset();
        resetStepUps(parts);
        positions = defaultPositions();
        timerDue = new long[MAX_PARTS];
        windingHigh = 0;
        logicHigh = logicPowered = 0;
        logicWords = new int[MAX_PARTS]; logicInputs = new int[MAX_PARTS];
        pausedClocks = 0;
        closedMask = 0;
        latchedMask = 0;
        blownFuses = 0;
        switches = 0;
        pressedComponents = 0;
        java.util.Arrays.fill(buttonExpiries, 0);
        this.rating = rating;
        circuit = new Circuit(parts, terminals);
        deviceSD.setDirty();
    }

    private void resetStepUps(List<Part> parts) {
        stepUps = new PcbStepUpTransformer[MAX_PARTS];
        bridges = new PcbBridgeRectifier[MAX_PARTS];
        optos = new PcbOptocoupler[MAX_PARTS];
        for(int i=0;i<parts.size();i++) if(parts.get(i).kind == Kind.OPTOCOUPLER) optos[i]=new PcbOptocoupler(pos,i);
        sensors = new PcbSensor[MAX_PARTS];
        for(int i=0;i<parts.size();i++) if(parts.get(i).kind.sensor()) sensors[i]=new PcbSensor(level,pos,i);
        regulators = new PcbVoltageRegulator[MAX_PARTS];
        for (int i=0;i<parts.size();i++) if(parts.get(i).kind == Kind.VOLTAGE_REGULATOR) regulators[i]=new PcbVoltageRegulator(pos,i);
        for (int i=0;i<parts.size();i++) if (parts.get(i).kind == Kind.BRIDGE_RECTIFIER) bridges[i] = new PcbBridgeRectifier(pos,MAX_NODES + MAX_PARTS * 3 + i * 4);
        for (int i=0;i<parts.size();i++) if (parts.get(i).kind == Kind.STEP_UP_TRANSFORMER) stepUps[i] = new PcbStepUpTransformer(pos,i);
    }

    public void interact(boolean toggle, long now) {
        if (burnt) return;
        if (toggle) {
            toggleRequested = !toggleRequested;
        } else {
            buttonUntil = now + 20;
        }
    }

    void interactComponent(int ordinal, long now) { interactComponent(ordinal, now, false); }

    String interactComponent(int ordinal, long now, boolean reset) {
        if (burnt) return null;
        for (int i = 0; i < circuit.parts.size(); i++) {
            Kind kind = circuit.parts.get(i).kind;
            if (kind == Kind.TRACE || ordinal-- != 0) continue;
            if (kind == Kind.OPTOCOUPLER) return optos[i].status();
            if (kind.sensor()) return sensors[i].status();
            if (kind == Kind.VOLTAGE_REGULATOR) return regulators[i].status();
            if (kind == Kind.STEP_UP_TRANSFORMER) return stepUps[i].status();
            if (kind.supply()) return onboardPower.status(circuit.parts.get(i).threshold);
            if (kind == Kind.SWITCH) {
                switches ^= 1L << i;
                deviceSD.setDirty();
            } else if (kind == Kind.BUTTON) {
                buttonExpiries[i] = now + 20;
            } else if (kind == Kind.MECHANICAL_TIMER || kind == Kind.CLOCK) {
                long[] copy = timerDue.clone();
                copy[i] = kind == Kind.MECHANICAL_TIMER ? (reset ? 0 : now + (long) circuit.parts.get(i).threshold) : 0;
                timerDue = copy;
                if (kind == Kind.CLOCK) {
                    if (reset) pausedClocks &= ~(1L << i);
                    else pausedClocks ^= 1L << i;
                }
                deviceSD.setDirty();
                if (kind == Kind.MECHANICAL_TIMER) return reset ? "Таймер отменён" : "Таймер заведён";
                return (pausedClocks & (1L << i)) != 0 ? "Клок остановлен" : reset ? "Фаза клока сброшена" : "Клок запущен";
            }
            return null;
        }
        return null;
    }

    boolean adjustComponent(int ordinal, int value) {
        if (burnt || value < 0 || value > 100) return false;
        for (int i = 0; i < circuit.parts.size(); i++) {
            Kind kind = circuit.parts.get(i).kind;
            if (kind == Kind.TRACE || ordinal-- != 0) continue;
            if (kind != Kind.RHEOSTAT && kind != Kind.POTENTIOMETER && kind != Kind.THREE_POSITION_SWITCH) return false;
            int[] copy = positions.clone();
            copy[i] = kind == Kind.THREE_POSITION_SWITCH ? selectorPosition(value) : value;
            positions = copy;
            deviceSD.setDirty();
            return true;
        }
        return false;
    }

    static int selectorPosition(int value) { return value < 34 ? 0 : value > 66 ? 100 : 50; }

    private double segmentResistance(Part part, int index, boolean upper) {
        double fraction = positions[index] / 100.0;
        return Math.max(CONTACT, part.ohms * (upper ? 1 - fraction : fraction));
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
            if (part.kind.supply()) { onboardPower.preTick(collector, part); continue; }
            if (part.kind == Kind.STEP_UP_TRANSFORMER) { stepUps[i].preTick(collector,part); continue; }
            if (part.kind == Kind.OPTOCOUPLER) { optos[i].preTick(collector,part); continue; }
            if (part.kind.sensor()) { sensors[i].preTick(collector,part); continue; }
            if (part.kind == Kind.VOLTAGE_REGULATOR) { regulators[i].preTick(collector,part); continue; }
            if (part.kind == Kind.BRIDGE_RECTIFIER) { bridges[i].preTick(collector,part); continue; }
            int branch = MAX_NODES + i;
            double sense = senseResistance(part);
            int returnNode = part.kind == Kind.POTENTIOMETER ? part.coilA : part.b;
            builder.node(branch).resistor(branch, returnNode, sense);
            if (current.reactive[i] != null) {
                boolean diode = part.kind == Kind.DIODE || part.kind.led();
                // DiodeProperties EE считает прямым напряжение n2−n1: анод — второй узел.
                builder.connect(diode ? branch : part.a, diode ? part.a : branch, current.reactive[i]);
            } else {
                builder.resistor(part.a, branch, resistance(part, i) - sense);
            }
            if (PcbLogic.chip(part.kind)) {
                int inputs=PcbLogic.inputs(part.kind);
                for (int input=0;input<inputs;input++) builder.resistor(part.pins[input],part.b,OPEN);
                for (int output=0;output<PcbLogic.outputs(part.kind);output++)
                    builder.resistor(part.pins[inputs+output],chipRail(part,i,output),logicResistance(i));
            }
            if (part.poweredLogic()) {
                // Прямое ребро вход—земля затирает нижнюю ветвь внешнего потенциометра.
                int inputNode=14000+2*i;
                builder.node(inputNode).resistor(part.coilA,inputNode,OPEN/2).resistor(inputNode,part.b,OPEN/2);
                if (part.kind == Kind.COMPARATOR) builder.node(inputNode+1).resistor(part.coilB,inputNode+1,OPEN/2).resistor(inputNode+1,part.b,OPEN/2);
                builder.resistor(part.logicOutput(),logicRail(part,i),logicResistance(i));
            }
            if (part.windingInput()) builder.resistor(part.coilA, part.a, part.ohms);
            if (part.kind == Kind.POTENTIOMETER) {
                builder.resistor(part.coilA, part.b, segmentResistance(part, i, true));
            }
            if (part.changeover()) {
                int alternate = MAX_NODES + MAX_PARTS * 2 + i;
                double ohms = part.kind == Kind.THREE_POSITION_SWITCH ? (positions[i] == 100 ? CONTACT : OPEN)
                        : resistance(part, i) == CONTACT ? OPEN : CONTACT;
                builder.node(alternate).resistor(part.a, alternate, ohms - CONTACT / 2).resistor(alternate, part.alternateNode(), CONTACT / 2);
            }
            if (part.kind == Kind.LATCHING_RELAY) {
                builder.resistor(part.coilA, part.alternate, part.ohms).resistor(part.coilB, part.alternate, part.ohms);
            }
            if (part.kind.controlled()) {
                int coil = MAX_NODES + MAX_PARTS + i;
                double half = coilResistance(part) / 2;
                builder.node(coil).resistor(part.coilA, coil, half).resistor(coil, part.coilB, half);
            }
        }
    }

    private int chipRail(Part part,int index,int output) {
        return (logicPowered & (1L<<index))!=0 && PcbLogic.output(part.kind,logicWords[index],output) ? part.a : part.b;
    }

    private int logicRail(Part part,int index) {
        long bit=1L<<index;
        return (logicPowered & logicHigh & bit)!=0 ? part.a : part.b;
    }

    private double logicResistance(int index) { return (logicPowered & (1L<<index))!=0 ? LOGIC_DRIVER_OHMS : OPEN; }

    private static double coilResistance(Part part) {
        return part.kind == Kind.RELAY ? part.ohms : OPEN;
    }

    private static double senseResistance(Part part) {
        return switch (part.kind) {
            case CAPACITOR -> CAPACITOR_ESR;
            case DIODE -> DIODE_SENSE;
            case LED, LED_PANEL, LED_STRIP -> indicatorResistance(part.ohms);
            default -> CONTACT / 2;
        };
    }

    private boolean switchActive(int index) { return switched != ((switches & (1L << index)) != 0); }

    private double resistance(Part part, int index) {
        long bit = 1L << index;
        return switch (part.kind) {
            case TRACE, AMMETER -> CONTACT;
            case RHEOSTAT, POTENTIOMETER -> segmentResistance(part, index, false);
            // У индикатора есть встроенный балласт, в том числе на уже установленных платах.
            case LAMP -> indicatorResistance(part.ohms);
            case VOLTMETER, REDSTONE_OUT -> OPEN;
            case BUTTON -> pressed || (pressedComponents & bit) != 0 ? CONTACT : OPEN;
            case SWITCH -> switchActive(index) ? CONTACT : OPEN;
            case THREE_POSITION_SWITCH -> positions[index] == 0 ? CONTACT : OPEN;
            case REDSTONE_IN -> powered ? CONTACT : OPEN;
            case MECHANICAL_TIMER -> (closedMask & bit) != 0 ? CONTACT : OPEN;
            case LATCHING_RELAY -> (latchedMask & bit) != 0 ? CONTACT : OPEN;
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
        double maxVoltage = 0, maxInsulationVoltage = 0;
        Circuit current = circuit;
        int[] groups = current.groups;
        for (int a = 0; a < current.terminals; a++) {
            for (int b = a + 1; b < current.terminals; b++) {
                if (current.highVoltageGroups[a] == current.highVoltageGroups[b])
                    maxInsulationVoltage = Math.max(maxInsulationVoltage,Math.sqrt(results.getVoltageAtSqr(pos,a,b)));
                if (groups[a] == groups[b]) {
                    maxVoltage = Math.max(maxVoltage, Math.sqrt(results.getVoltageAtSqr(pos, a, b)));
                }
            }
        }
        double maxCurrent = 0;
        boolean invalidBridge = false;
        long nextFuses = blownFuses;
        for (int i = 0; i < current.parts.size(); i++) {
            Part part = current.parts.get(i);
            if (part.kind.supply()) {
                onboardPower.postTick(results, part);
                deviceSD.setDirty();
                watts += onboardPower.losses();
                maxVoltage = Math.max(maxVoltage, onboardPower.output());
                maxCurrent = Math.max(maxCurrent, onboardPower.current());
                continue;
            }
            if (part.kind == Kind.STEP_UP_TRANSFORMER) {
                stepUps[i].postTick(results,part);
                watts += stepUps[i].losses();
                maxVoltage = Math.max(maxVoltage,stepUps[i].input());
                maxInsulationVoltage = Math.max(maxInsulationVoltage,stepUps[i].output());
                maxCurrent = Math.max(maxCurrent,stepUps[i].current());
                deviceSD.setDirty();
                continue;
            }
            if (part.kind == Kind.OPTOCOUPLER) { var m=optos[i].measure(results,part); watts+=m.watts(); maxVoltage=Math.max(maxVoltage,m.volts()); maxCurrent=Math.max(maxCurrent,m.amps()); deviceSD.setDirty(); continue; }
            if (part.kind.sensor()) {
                var m=sensors[i].measure(results,part); watts+=m.watts(); maxVoltage=Math.max(maxVoltage,m.volts()); maxCurrent=Math.max(maxCurrent,m.amps()); deviceSD.setDirty(); continue;
            }
            if (part.kind == Kind.BRIDGE_RECTIFIER || part.kind == Kind.VOLTAGE_REGULATOR) {
                var measurement=part.kind == Kind.BRIDGE_RECTIFIER ? bridges[i].measure(results,part) : regulators[i].measure(results,part);
                deviceSD.setDirty();
                invalidBridge |= !Double.isFinite(measurement.volts()) || !Double.isFinite(measurement.watts()) || !Double.isFinite(measurement.amps());
                watts += measurement.watts();
                maxVoltage=Math.max(maxVoltage,measurement.volts());
                maxCurrent=Math.max(maxCurrent,measurement.amps());
                continue;
            }
            double squared = results.getVoltageAtSqr(pos, part.a, part.b);
            maxVoltage = Math.max(maxVoltage, Math.sqrt(squared));
            double sense = senseResistance(part);
            int returnNode = part.kind == Kind.POTENTIOMETER ? part.coilA : part.b;
            double sensedSquared = results.getVoltageAtSqr(pos, MAX_NODES + i, returnNode);
            double amps = Math.sqrt(sensedSquared) / sense;
            if (part.kind == Kind.POTENTIOMETER) {
                double upperSquared = results.getVoltageAtSqr(pos, part.coilA, part.b);
                double upperOhms = segmentResistance(part, i, true);
                watts += Math.sqrt(results.getVoltageAtSqr(pos, part.a, part.coilA)) * amps + upperSquared / upperOhms;
                maxCurrent = Math.max(maxCurrent, Math.sqrt(upperSquared) / upperOhms);
            } else if (part.kind == Kind.CAPACITOR) {
                watts += sensedSquared / sense;
                maxVoltage = Math.max(maxVoltage, voltage(results, part.a, MAX_NODES + i));
            } else {
                watts += Math.sqrt(squared) * amps;
            }
            maxCurrent = Math.max(maxCurrent, amps);
            if (part.changeover()) {
                double alternateSquared = results.getVoltageAtSqr(pos, part.a, part.alternateNode());
                double alternateSense = results.getVoltageAtSqr(pos, MAX_NODES + MAX_PARTS * 2 + i, part.alternateNode());
                double alternateAmps = Math.sqrt(alternateSense) / (CONTACT / 2);
                watts += Math.sqrt(alternateSquared) * alternateAmps;
                maxVoltage = Math.max(maxVoltage, Math.sqrt(alternateSquared));
                maxCurrent = Math.max(maxCurrent, alternateAmps);
            }
            if (PcbLogic.chip(part.kind)) {
                int inputs=PcbLogic.inputs(part.kind);
                for (int pin=0;pin<inputs;pin++) {
                    double v2=results.getVoltageAtSqr(pos,part.pins[pin],part.b);
                    watts+=v2/OPEN; maxVoltage=Math.max(maxVoltage,Math.sqrt(v2));
                }
                for (int output=0;output<PcbLogic.outputs(part.kind);output++) {
                    int pin=part.pins[inputs+output];
                    double v2=results.getVoltageAtSqr(pos,pin,chipRail(part,i,output)), ohms=logicResistance(i);
                    watts+=v2/ohms; maxCurrent=Math.max(maxCurrent,Math.sqrt(v2)/ohms);
                    maxVoltage=Math.max(maxVoltage,voltage(results,pin,part.b));
                }
            }
            if (part.poweredLogic()) {
                double driver = results.getVoltageAtSqr(pos,part.logicOutput(),logicRail(part,i));
                double driverOhms=logicResistance(i);
                double inputA=results.getVoltageAtSqr(pos,part.coilA,part.b), inputB=part.kind == Kind.COMPARATOR ? results.getVoltageAtSqr(pos,part.coilB,part.b) : 0;
                watts += driver/driverOhms + (inputA+inputB)/OPEN;
                maxCurrent=Math.max(maxCurrent,Math.sqrt(driver)/driverOhms);
                maxVoltage=Math.max(maxVoltage,Math.sqrt(Math.max(inputA,Math.max(inputB,results.getVoltageAtSqr(pos,part.logicOutput(),part.b)))));
            }
            if (part.windingInput()) {
                double windSquared = results.getVoltageAtSqr(pos, part.coilA, part.a);
                watts += windSquared / part.ohms;
                maxVoltage = Math.max(maxVoltage, Math.sqrt(windSquared));
                maxCurrent = Math.max(maxCurrent, Math.sqrt(windSquared) / part.ohms);
            }
            if (part.kind == Kind.FUSE && amps > part.threshold / 100) nextFuses |= 1L << i;
            if (part.kind == Kind.LATCHING_RELAY) {
                double setSquared = results.getVoltageAtSqr(pos, part.coilA, part.alternate);
                double resetSquared = results.getVoltageAtSqr(pos, part.coilB, part.alternate);
                watts += (setSquared + resetSquared) / part.ohms;
                maxVoltage = Math.max(maxVoltage, Math.sqrt(Math.max(setSquared, resetSquared)));
                maxCurrent = Math.max(maxCurrent, (Math.sqrt(setSquared) + Math.sqrt(resetSquared)) / part.ohms);
            }
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
        if (invalidBridge || maxVoltage > rating.volts + 1e-6 || maxInsulationVoltage > 480 + 1e-6 || overload >= OVERLOAD_TICKS) {
            burnt = true;
            deviceSD.setDirty();
            level.setBlockAndUpdate(pos, state.setValue(PcbCircuitBlock.BURNT, true)
                    .setValue(PcbCircuitBlock.LIT, false).setValue(PcbCircuitBlock.OUTPUT, 0));
            return;
        }
        long now = level.getGameTime();
        pressed = now < buttonUntil;
        long pressedMask = 0;
        for (int i = 0; i < current.parts.size(); i++) {
            if (now < buttonExpiries[i]) pressedMask |= 1L << i;
        }
        pressedComponents = pressedMask;
        if (toggleRequested) {
            switched = !switched;
            toggleRequested = false;
            deviceSD.setDirty();
        }
        Direction input = PcbCircuitBlock.mountedDirection(Direction.NORTH, state.getValue(PcbCircuitBlock.FACING));
        BlockPos neighbor = pos.relative(input);
        powered = level.isLoaded(neighbor) && level.getSignal(neighbor, input) > 0;
        boolean lit = false;
        double maxIndicatorVoltage = 0;
        int output = 0;
        long closed = 0;
        long nextLatched = latchedMask;
        long litIndicators = 0;
        long controlStates = 0;
        int[] controlPositions = new int[current.parts.size()];
        long[] nextTimers = timerDue.clone();
        long nextWinding = windingHigh;
        long nextLogicHigh=0, nextLogicPowered=0;
        int[] nextWords=logicWords.clone(), nextInputs=logicInputs.clone();
        double[] nextControls = new double[current.parts.size()];
        int component = -1;
        meterKind = null;
        meterValue = 0;
        for (int i = 0; i < current.parts.size(); i++) {
            Part part = current.parts.get(i);
            if (part.kind != Kind.TRACE) {
                component++;
                controlPositions[component] = positions[i];
            }
            if (part.kind == Kind.BUTTON && (pressed || (pressedComponents & (1L << i)) != 0)
                    || part.kind == Kind.THREE_POSITION_SWITCH && positions[i] != 50
                    || part.kind == Kind.SWITCH && (switched != ((switches & (1L << i)) != 0)))
                controlStates |= 1L << component;
            switch (part.kind) {
                case D_FLIP_FLOP, COUNTER, REGISTER, DECODER -> {
                    long bit=1L<<i;
                    double supply=signedVoltage(results,part.a,part.b);
                    boolean hasPower=supply>=3 && voltage(results,part.a,part.b)<=6;
                    if (!hasPower) { nextWords[i]=0; nextInputs[i]=0; }
                    else {
                        nextLogicPowered |= bit;
                        int inputs=logicInputs[i];
                        for (int pin=0;pin<PcbLogic.inputs(part.kind);pin++) {
                            double v=signedVoltage(results,part.pins[pin],part.b);
                            if (v>=supply*.6) inputs |= 1<<pin;
                            else if (v<=supply*.4) inputs &= ~(1<<pin);
                        }
                        int previous=(logicPowered & bit)==0 ? inputs : logicInputs[i];
                        nextWords[i]=PcbLogic.next(part.kind,logicWords[i],inputs,previous);
                        nextInputs[i]=inputs;
                        if (nextWords[i]!=0) controlStates |= 1L<<component;
                    }
                    controlPositions[component]=nextWords[i];
                }
                case COMPARATOR -> {
                    long bit=1L<<i;
                    double supply=signedVoltage(results,part.a,part.b);
                    boolean enabled=supply>=3 && voltage(results,part.a,part.b)<=6;
                    if (enabled) {
                        nextLogicPowered |= bit;
                        double difference=signedVoltage(results,part.coilA,part.b)-signedVoltage(results,part.coilB,part.b);
                        nextControls[i]=difference;
                        if (difference>0.001) { nextLogicHigh |= bit; controlStates |= 1L<<component; }
                    }
                }
                case MECHANICAL_TIMER -> {
                    long bit = 1L << i;
                    if (part.windingInput()) {
                        boolean high = voltage(results, part.coilA, part.a) >= 3;
                        if (high && (windingHigh & bit) == 0) nextTimers[i] = now + (long) part.threshold;
                        if (high) nextWinding |= bit; else nextWinding &= ~bit;
                    }
                    if (nextTimers[i] > 0 && now >= nextTimers[i]) {
                        closed |= 1L << i;
                        controlStates |= 1L << component;
                    }
                    controlPositions[component] = nextTimers[i] == 0 ? 0 : (int) Math.clamp(
                            Math.ceil(100.0 * (nextTimers[i] - now) / part.threshold), 0, 100);
                }
                case CLOCK -> {
                    long bit=1L<<i;
                    controlPositions[component]=0;
                    double supply=signedVoltage(results,part.a,part.b);
                    boolean hasPower=supply>=3 && voltage(results,part.a,part.b)<=6;
                    if (hasPower) nextLogicPowered |= bit;
                    boolean enabled=hasPower && (pausedClocks & bit)==0 && signedVoltage(results,part.coilA,part.b)>=supply*.6;
                    if (!enabled) nextTimers[i]=0;
                    else {
                        if (nextTimers[i]==0) nextTimers[i]=now;
                        long phase=Math.floorMod(now-nextTimers[i],(long)part.threshold);
                        if (phase<part.threshold/2) { nextLogicHigh |= bit; controlStates |= 1L<<component; }
                        controlPositions[component]=(int)(100*phase/part.threshold);
                    }
                }
                case LED, LED_PANEL, LED_STRIP -> {
                    double volts = voltage(results, part.a, part.b);
                    double ballastVoltage = voltage(results, MAX_NODES + i, part.b);
                    if (volts >= part.threshold && ballastVoltage / senseResistance(part) >= 0.001) {
                        lit = true;
                        litIndicators |= 1L << component;
                    }
                }
                case LATCHING_RELAY -> {
                    long bit = 1L << i;
                    double set = voltage(results, part.coilA, part.alternate), reset = voltage(results, part.coilB, part.alternate);
                    nextControls[i] = set;
                    // Механическая фиксация переживает снятие питания; одновременная запись сбрасывает флаг.
                    if (reset >= part.threshold) nextLatched &= ~bit;
                    else if (set >= part.threshold) nextLatched |= bit;
                    if ((nextLatched & bit) != 0) controlStates |= 1L << component;
                }
                case RELAY, TRANSISTOR -> {
                    long bit = 1L << i;
                    double threshold = part.kind == Kind.RELAY && (closedMask & bit) != 0 ? part.threshold * 0.8 : part.threshold;
                    double control = part.kind == Kind.RELAY ? voltage(results, part.coilA, part.coilB)
                            : signedVoltage(results, part.coilA, part.coilB);
                    nextControls[i] = control;
                    if (control >= threshold) closed |= bit;
                }
                case LAMP -> {
                    double volts = voltage(results, part.a, part.b);
                    maxIndicatorVoltage = Math.max(maxIndicatorVoltage, volts);
                    if (volts >= part.threshold) {
                        lit = true;
                        litIndicators |= 1L << component;
                    }
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
        if (level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board) board.setVisualStates(litIndicators, controlStates, controlPositions);
        if (nextWinding != windingHigh) { windingHigh = nextWinding; deviceSD.setDirty(); }
        if (!java.util.Arrays.equals(timerDue, nextTimers)) deviceSD.setDirty();
        timerDue = nextTimers;
        closedMask = closed;
        logicHigh=nextLogicHigh; logicPowered=nextLogicPowered;
        if (!java.util.Arrays.equals(logicWords,nextWords) || !java.util.Arrays.equals(logicInputs,nextInputs)) deviceSD.setDirty();
        logicWords=nextWords; logicInputs=nextInputs;
        if (latchedMask != nextLatched) { latchedMask = nextLatched; deviceSD.setDirty(); }
        controlVoltages = nextControls;
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

    double controlVoltage(int part) {
        double[] values = controlVoltages;
        return part >= 0 && part < values.length ? values[part] : 0;
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
        onboardPower.read(tag.getCompound("OnboardPower"));
        stepUps = new PcbStepUpTransformer[MAX_PARTS];
        circuit = new Circuit(List.of(), LEGACY_TERMINALS);
        timerDue = new long[MAX_PARTS];
        windingHigh = 0;
        logicHigh = logicPowered = 0;
        logicWords = new int[MAX_PARTS]; logicInputs = new int[MAX_PARTS];
        long[] savedTimers = tag.getLongArray("TimerDue");
        for (int i = 0; i < Math.min(savedTimers.length, timerDue.length); i++) timerDue[i] = Math.max(0, savedTimers[i]);
        int[] savedWords=tag.getIntArray("LogicWords"), savedInputs=tag.getIntArray("LogicInputs");
        for (int i=0;i<Math.min(savedWords.length,MAX_PARTS);i++) logicWords[i]=savedWords[i]&15;
        for (int i=0;i<Math.min(savedInputs.length,MAX_PARTS);i++) logicInputs[i]=savedInputs[i]&31;
        windingHigh = tag.getLong("WindingHigh");
        pausedClocks = tag.getLong("PausedClocks");
        positions = defaultPositions();
        int[] savedPositions = tag.getIntArray("Positions");
        for (int i = 0; i < Math.min(savedPositions.length, positions.length); i++)
            positions[i] = Math.clamp(savedPositions[i], 0, 100);
        switched = tag.getBoolean("Switch");
        switches = tag.getLong("Switches");
        latchedMask = tag.getLong("Latched");
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
                        item.getBoolean("NormallyClosed"), item.contains("ModuleIndex") ? item.getInt("ModuleIndex") : -1,
                        item.contains("ModuleType") ? item.getInt("ModuleType") : -1, item.contains("Alternate") ? item.getInt("Alternate") : -1,item.getIntArray("Pins"));
                if (!part.valid() || part.moduleIndex >= 0 && tag.getInt("CircuitVersion") != PcbTopology.VERSION) return;
                parts.add(part);
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }
        if (parts.stream().filter(part -> part.kind.supply()).count() > 1) return;
        int terminals = tag.contains("Terminals") ? Math.clamp(tag.getInt("Terminals"), 0, MAX_TERMINALS) : LEGACY_TERMINALS;
        circuit = new Circuit(parts, terminals);
        resetStepUps(parts);
        for(int i=0;i<parts.size();i++) if(optos[i]!=null) optos[i].read(tag.getCompound("Optocouplers").getCompound(Integer.toString(i)));
        for(int i=0;i<parts.size();i++) if(regulators[i]!=null) regulators[i].read(tag.getCompound("RegulatorPower").getCompound(Integer.toString(i)));
        var savedStepUps = tag.getCompound("StepUpPower");
        for (int i=0;i<parts.size();i++) if (stepUps[i] != null) stepUps[i].read(savedStepUps.getCompound(Integer.toString(i)));
        ListTag charges = tag.getList("Charge", Tag.TAG_DOUBLE);
        for (int i = 0; i < Math.min(charges.size(), circuit.reactive.length); i++) {
            if (circuit.reactive[i] instanceof CapacitorProperties capacitor && charges.get(i) instanceof net.minecraft.nbt.DoubleTag charge)
                capacitor.lastVoltage = Math.clamp(charge.getAsDouble(), -rating.volts, rating.volts);
        }
    }

    @Override
    public void write(CompoundTag tag) {
        var supply = new CompoundTag();
        onboardPower.write(supply);
        tag.put("OnboardPower", supply);
        var highVoltage = new CompoundTag();
        for (int i=0;i<circuit.parts.size();i++) if (stepUps[i] != null) {
            var saved = new CompoundTag(); stepUps[i].write(saved); highVoltage.put(Integer.toString(i),saved);
        }
        tag.put("StepUpPower",highVoltage);
        var bridgeMeasurements=new CompoundTag();
        for(int i=0;i<circuit.parts.size();i++) if(bridges[i]!=null) {var saved=new CompoundTag();bridges[i].write(saved);bridgeMeasurements.put(Integer.toString(i),saved);}
        tag.put("BridgeMeasurements",bridgeMeasurements);
        var regulatorPower=new CompoundTag();
        for(int i=0;i<circuit.parts.size();i++) if(regulators[i]!=null){var saved=new CompoundTag();regulators[i].write(saved);regulatorPower.put(Integer.toString(i),saved);}
        tag.put("RegulatorPower",regulatorPower);
        var optoData=new CompoundTag();
        for(int i=0;i<circuit.parts.size();i++) if(optos[i]!=null){var saved=new CompoundTag();optos[i].write(saved);optoData.put(Integer.toString(i),saved);}
        tag.put("Optocouplers",optoData);
        var sensorData=new CompoundTag();
        for(int i=0;i<circuit.parts.size();i++) if(sensors[i]!=null){var saved=new CompoundTag();sensors[i].write(saved);sensorData.put(Integer.toString(i),saved);}
        tag.put("Sensors",sensorData);
        tag.putInt("CircuitVersion", PcbTopology.VERSION);
        tag.putInt("Terminals", circuit.terminals);
        tag.putLongArray("TimerDue", timerDue);
        tag.putLong("WindingHigh", windingHigh);
        tag.putIntArray("LogicWords",logicWords); tag.putIntArray("LogicInputs",logicInputs);
        tag.putLong("PausedClocks", pausedClocks);
        tag.putIntArray("Positions", positions);
        tag.putBoolean("Switch", switched);
        tag.putLong("Switches", switches);
        tag.putLong("Latched", latchedMask);
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
            if (part.pins.length>0) item.putIntArray("Pins",part.pins);
            item.putInt("A", part.a);
            item.putInt("B", part.b);
            item.putDouble("Ohms", part.ohms);
            item.putInt("CoilA", part.coilA);
            item.putInt("CoilB", part.coilB);
            item.putDouble("Threshold", part.threshold);
            if (part.moduleIndex >= 0) { item.putInt("ModuleIndex", part.moduleIndex); item.putInt("ModuleType", part.moduleType); }
            if (part.alternate >= 0) item.putInt("Alternate", part.alternate);
            if (part.normallyClosed) item.putBoolean("NormallyClosed", true);
            serialized.add(item);
        }
        tag.put("Circuit", serialized);
    }
}
