package ee.extras.pcb;

import ee.extras.pcb.PcbCircuitDevice.Kind;
import ee.extras.pcb.PcbCircuitDevice.Part;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Электрические компоненты редактора и преобразование дорожек в узлы EE. */
final class PcbWiring {
    static final String KEY = "ElectricalParts";
    static final double[] OHMS = {10, 47, 100, 220, 470, 1000, 2200, 4700, 10000};
    static final int[] TIMER_TICKS = {20, 40, 100, 200, 400, 1200, 2400, 6000, 12000};
    static final int[] CLOCK_TICKS = {2, 4, 10, 20, 40, 100, 200, 1200, 12000};
    static final double[] VOLTS = {3, 5, 12, 24, 48, 96, 128, 230};
    static final Kind[] KINDS = Arrays.stream(PcbModules.ALL).map(PcbModules.Module::kind).toArray(Kind[]::new);
    static final String ERROR = "Проверьте выводы и контакты";
    private static final int CELLS = PcbTopology.CELLS;
    private static final int SIZE = PcbTopology.SIZE;

    private PcbWiring() {}

    static int component(int type, int rotation, int resistance, int threshold, boolean normallyClosed) {
        return 1 + type + ((rotation & 3) << 8) + (resistance << 10) + (threshold << 14)
                + (normallyClosed ? 1 << 17 : 0);
    }

    static int type(int definition) { return (definition - 1) & 255; }
    static int rotation(int definition) { return ((definition - 1) >>> 8) & 3; }
    static boolean vertical(int definition) { return (rotation(definition) & 1) != 0; }
    static int resistance(int definition) { return ((definition - 1) >>> 10) & 15; }
    static int threshold(int definition) { return ((definition - 1) >>> 14) & 7; }
    static boolean normallyClosed(int definition) { return ((definition - 1) & (1 << 17)) != 0; }
    static boolean valid(int definition) {
        return definition > 0 && definition <= 1 << 18 && type(definition) < KINDS.length
                && resistance(definition) < OHMS.length;
    }

    static int minimumLevel(int type) { return PcbModules.ALL[type].level(); }

    static int period(int type, int setting) {
        return (KINDS[type] == Kind.MECHANICAL_TIMER ? TIMER_TICKS : CLOCK_TICKS)[setting];
    }

    static int pinCell(int cell, int definition, int pin) {
        return PcbFootprint.pinCell(cell, definition, pin);
    }

    static boolean active(CompoundTag tag) {
        for (int value : tag.getIntArray(KEY)) if (value != 0) return true;
        return false;
    }

    static List<Part> compile(CompoundTag tag) {
        if (tag.getInt("Version") != PcbTopology.VERSION || !PcbFootprint.validate(tag) || !PcbParameters.valid(tag)) return null;
        int[] definitions = Arrays.copyOf(tag.getIntArray(KEY), PcbTopology.MAX_LAYERS * CELLS);
        byte[] cells = tag.getByteArray("Cells");
        int size = PcbTopology.level(tag) * CELLS;
        if (cells.length < size) return null;
        int[] groups = new int[size];
        Arrays.fill(groups, -1);
        for (int i = 0; i < size; i++) {
            if (conductor(cells[i])) groups[i] = i;
        }
        for (int i = 0; i < size; i++) {
            if (groups[i] < 0) continue;
            int x = i % SIZE;
            int y = i % CELLS / SIZE;
            if (x > 0 && groups[i - 1] >= 0 && PcbTopology.connected(tag, i / CELLS, i % CELLS, i % CELLS - 1)) join(groups, i, i - 1);
            if (y > 0 && groups[i - SIZE] >= 0 && PcbTopology.connected(tag, i / CELLS, i % CELLS, i % CELLS - SIZE)) join(groups, i, i - SIZE);
            if (cells[i] == PcbTopology.VIA) {
                for (int j = i % CELLS; j < size; j += CELLS) {
                    if (groups[j] >= 0) join(groups, i, j);
                }
            }
        }
        List<Part> parts = new ArrayList<>();
        int[] nodes = new int[size];
        Arrays.fill(nodes, -1);
        boolean positioned = PcbTopology.positionedContacts(tag);
        int terminals = 0;
        for (int i = 0; i < size; i++) {
            if (cells[i] != PcbTopology.TERMINAL) continue;
            if ((!positioned && terminals == 4) || i >= CELLS || !edge(i)) return null;
            int group = root(groups, i);
            if (nodes[group] < 0) nodes[group] = terminals;
            else parts.add(new Part(Kind.TRACE, nodes[group], terminals, 1, 0, 0, 1));
            terminals++;
        }
        if (!positioned && terminals < 2) return null;
        int nextNode = positioned ? terminals : 4;
        for (int i = 0; i < size; i++) {
            if (cells[i] != PcbTopology.COMPONENT) continue;
            int definition = definitions[i];
            int type = type(definition);
            int resistance = resistance(definition);
            int threshold = threshold(definition);
            if (!valid(definition))
                return null;
            Kind kind = KINDS[type];
            var module = PcbModules.get(definition);
            int[] pins = new int[Math.max(4, module.pins())];
            for (int pin = 0; pin < PcbModules.get(definition).pins(); pin++) {
                int neighbor = pinCell(i, definition, pin);
                if (neighbor < 0 || groups[neighbor] < 0) return null;
                int group = root(groups, neighbor);
                if (nodes[group] < 0) nodes[group] = nextNode++;
                pins[pin] = nodes[group];
            }
            var parameters = PcbParameters.get(tag, i);
            double nominal = PcbParameters.nominal(definition, parameters);
            double limit = PcbParameters.threshold(definition, parameters) * (PcbParameters.amps(module) ? 100 : 1);
            boolean normallyClosed = kind == Kind.RELAY && normallyClosed(definition);
            for (int section = 0; section < module.sections(); section++) {
                int first = module.sections() > 1 ? section * 2 : 0;
                Part part = new Part(kind, pins[first], pins[first + 1], module.timed() ? OHMS[resistance] : nominal,
                        module.sections() > 1 ? 0 : pins[2], module.sections() > 1 ? 0 : kind == Kind.TRANSISTOR ? pins[1] : pins[3],
                        module.timed() ? Math.round(nominal * 20) : limit, normallyClosed, i, type);
                if (!part.valid() || parts.size() == 64) return null;
                parts.add(part);
            }
        }
        return parts.isEmpty() || parts.size() > 64 ? null : List.copyOf(parts);
    }

    static int componentOrdinal(int[] definitions, int index) {
        int ordinal = 0;
        for (int i = 0; i < index; i++) if (definitions[i] != 0) ordinal += PcbModules.get(definitions[i]).sections();
        return ordinal;
    }

    static boolean conductor(int cell) {
        return cell == PcbTopology.TRACE || cell == PcbTopology.VIA || cell == PcbTopology.TERMINAL;
    }

    private static boolean edge(int i) {
        return neighbor(i, false, -1) < 0 || neighbor(i, false, 1) < 0
                || neighbor(i, true, -1) < 0 || neighbor(i, true, 1) < 0;
    }

    private static int neighbor(int i, boolean vertical, int sign) {
        int axis = vertical ? i % CELLS / SIZE : i % SIZE;
        int next = axis + sign;
        return next >= 0 && next < SIZE ? i + sign * (vertical ? SIZE : 1) : -1;
    }

    private static int root(int[] groups, int i) {
        while (i != groups[i]) {
            groups[i] = groups[groups[i]];
            i = groups[i];
        }
        return i;
    }

    private static void join(int[] groups, int a, int b) {
        groups[root(groups, a)] = root(groups, b);
    }
}
