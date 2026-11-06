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
    static final double[] VOLTS = {3, 5, 12, 24, 48, 96, 128, 230};
    static final Kind[] KINDS = {Kind.RESISTOR, Kind.RELAY, Kind.BUTTON, Kind.SWITCH,
            Kind.LAMP, Kind.REDSTONE_IN, Kind.REDSTONE_OUT, Kind.CAPACITOR, Kind.DIODE,
            Kind.FUSE, Kind.VOLTMETER, Kind.AMMETER, Kind.TRANSISTOR};
    static final String ERROR = "Проверьте выводы и контакты";
    private static final int CELLS = PcbTopology.CELLS;
    private static final int SIZE = PcbTopology.SIZE;

    private PcbWiring() {}

    static int component(int type, int rotation, int resistance, int threshold, boolean normallyClosed) {
        return 1 + type + ((rotation & 1) == 1 ? 16 : 0) + resistance * 32 + threshold * 512 + (rotation >= 2 ? 4096 : 0)
                + (normallyClosed ? 8192 : 0);
    }

    static int type(int component) {
        return (component - 1) & 15;
    }

    static boolean vertical(int component) {
        return ((component - 1) & 16) != 0;
    }

    static int rotation(int definition) {
        return (vertical(definition) ? 1 : 0) + (((definition - 1) & 4096) != 0 ? 2 : 0);
    }

    static int minimumLevel(int type) {
        return switch (KINDS[type]) {
            case TRANSISTOR -> 3;
            case CAPACITOR, DIODE, VOLTMETER, AMMETER -> 2;
            default -> 1;
        };
    }

    static int pinCell(int cell, int definition, int pin) {
        boolean axis = pin < 2 ? vertical(definition) : !vertical(definition);
        boolean reverse = rotation(definition) >= 2;
        return neighbor(cell, axis, (pin % 2 == 0) != reverse ? -1 : 1);
    }

    static boolean active(CompoundTag tag) {
        for (int value : tag.getIntArray(KEY)) if (value != 0) return true;
        return false;
    }

    static List<Part> compile(CompoundTag tag) {
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
            if (x > 0 && groups[i - 1] >= 0) join(groups, i, i - 1);
            if (y > 0 && groups[i - SIZE] >= 0) join(groups, i, i - SIZE);
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
            int resistance = ((definition - 1) >>> 5) & 15;
            int threshold = ((definition - 1) >>> 9) & 7;
            if (definition <= 0 || definition > 16384 || type >= KINDS.length || resistance >= OHMS.length || threshold >= VOLTS.length)
                return null;
            Kind kind = KINDS[type];
            int[] pins = new int[4];
            for (int pin = 0; pin < (kind.controlled() ? 4 : 2); pin++) {
                int neighbor = pinCell(i, definition, pin);
                if (neighbor < 0 || groups[neighbor] < 0) return null;
                int group = root(groups, neighbor);
                if (nodes[group] < 0) nodes[group] = nextNode++;
                pins[pin] = nodes[group];
            }
            boolean normallyClosed = kind == Kind.RELAY && ((definition - 1) & 8192) != 0;
            Part part = new Part(kind, pins[0], pins[1], OHMS[resistance], pins[2], pins[3], VOLTS[threshold], normallyClosed);
            if (!part.valid() || parts.size() == 64) return null;
            parts.add(part);
        }
        return parts.isEmpty() || parts.size() > 64 ? null : List.copyOf(parts);
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
