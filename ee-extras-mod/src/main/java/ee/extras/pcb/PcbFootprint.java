package ee.extras.pcb;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/** Монтажная площадь корпуса и выводы. Якорь — левый верхний угол повёрнутого корпуса. */
final class PcbFootprint {
    static final String ERROR = "Корпус пересекает край, вырез, дорожку или другой модуль";
    private PcbFootprint() {}

    static int baseWidth(int definition) { return PcbModules.get(definition).width(); }
    static int baseHeight(int definition) { return PcbModules.get(definition).height(); }
    static int width(int definition) {
        return PcbWiring.vertical(definition) ? baseHeight(definition) : baseWidth(definition);
    }
    static int height(int definition) {
        return PcbWiring.vertical(definition) ? baseWidth(definition) : baseHeight(definition);
    }

    static boolean valid(int definition) {
        return PcbWiring.valid(definition);
    }

    static boolean contains(int anchor, int definition, int cell) {
        if (!valid(definition) || cell < 0 || cell >= PcbTopology.CELLS) return false;
        int x = cell % PcbTopology.SIZE - anchor % PcbTopology.SIZE;
        int y = cell / PcbTopology.SIZE - anchor / PcbTopology.SIZE;
        return x >= 0 && y >= 0 && x < width(definition) && y < height(definition);
    }

    static int owner(int[] definitions, int layer, int cell) {
        int start = layer * PcbTopology.CELLS;
        for (int i = start; i < Math.min(start + PcbTopology.CELLS, definitions.length); i++) {
            if (contains(i % PcbTopology.CELLS, definitions[i], cell)) return i;
        }
        return -1;
    }

    static int owner(CompoundTag tag, int layer, int cell) {
        return owner(tag.getIntArray(PcbWiring.KEY), layer, cell);
    }

    static boolean canPlace(CompoundTag tag, int layer, int anchor, int definition, int ignored) {
        if (!valid(definition) || layer != 0
                || anchor < 0 || anchor >= PcbTopology.CELLS) return false;
        int ax = anchor % PcbTopology.SIZE;
        int ay = anchor / PcbTopology.SIZE;
        if (ax + width(definition) > PcbTopology.SIZE || ay + height(definition) > PcbTopology.SIZE) return false;
        int[] definitions = tag.getIntArray(PcbWiring.KEY);
        for (int y = 0; y < height(definition); y++) {
            for (int x = 0; x < width(definition); x++) {
                int cell = anchor + y * PcbTopology.SIZE + x;
                int value = PcbTopology.get(tag, layer, cell);
                int owner = owner(definitions, layer, cell);
                for (int otherLayer = 0; otherLayer < PcbTopology.level(tag); otherLayer++) {
                    int other = owner(definitions, otherLayer, cell);
                    if (other >= 0 && other != ignored) return false;
                }
                if (PcbTopology.cut(tag, cell) || owner >= 0 && owner != ignored
                        || value != PcbTopology.EMPTY && !(ignored >= 0 && owner == ignored && value == PcbTopology.COMPONENT)) return false;
            }
        }
        return true;
    }

    /** Все выводы лежат за границей корпуса и поворачиваются вместе с ним. */
    static int pinCell(int anchor, int definition, int pin) {
        int w = baseWidth(definition);
        int h = baseHeight(definition);
        int x = PcbModules.get(definition).sections() > 1 ? (pin % 2 == 0 ? -1 : w) : switch (pin) { case 0 -> -1; case 1 -> w; default -> (w - 1) / 2; };
        int y = PcbModules.get(definition).sections() > 1 ? pin / 2 : switch (pin) { case 2 -> -1; case 3 -> h; default -> (h - 1) / 2; };
        for (int r = 0; r < PcbWiring.rotation(definition); r++) {
            int nextX = h - 1 - y;
            y = x;
            x = nextX;
            int nextW = h;
            h = w;
            w = nextW;
        }
        x += anchor % PcbTopology.SIZE;
        y += anchor / PcbTopology.SIZE;
        return x < 0 || y < 0 || x >= PcbTopology.SIZE || y >= PcbTopology.SIZE
                ? -1 : anchor / PcbTopology.CELLS * PcbTopology.CELLS + y * PcbTopology.SIZE + x;
    }

    static void isolatePins(CompoundTag tag, int layer, int anchor, int definition) {
        if (PcbModules.get(definition).sections() <= 1) return;
        int count = PcbModules.get(definition).pins();
        for (int a = 0; a < count; a++) {
            int first = pinCell(anchor, definition, a);
            if (first < 0) continue;
            for (int b = a + 1; b < count; b++) {
                if (a / 2 == b / 2) continue;
                int second = pinCell(anchor, definition, b);
                for (int direction = 0; direction < 4; direction++) {
                    if (PcbTopology.neighbor(first, direction) == second)
                        PcbTopology.setEdgeCut(tag, layer, first, direction, true);
                }
            }
        }
    }

    static boolean validate(CompoundTag tag) {
        int[] definitions = tag.getIntArray(PcbWiring.KEY);
        int[] occupied = new int[PcbTopology.CELLS];
        Arrays.fill(occupied, -1);
        for (int i = 0; i < definitions.length; i++) {
            int definition = definitions[i];
            if (definition == 0) continue;
            if (i >= PcbTopology.CELLS || !valid(definition)
                    || PcbWiring.minimumLevel(PcbWiring.type(definition)) > PcbTopology.level(tag)
                    || PcbTopology.get(tag, i / PcbTopology.CELLS, i % PcbTopology.CELLS) != PcbTopology.COMPONENT) return false;
            int anchor = i % PcbTopology.CELLS;
            if (anchor % PcbTopology.SIZE + width(definition) > PcbTopology.SIZE
                    || anchor / PcbTopology.SIZE + height(definition) > PcbTopology.SIZE) return false;
            for (int y = 0; y < height(definition); y++) {
                for (int x = 0; x < width(definition); x++) {
                    int index = i + y * PcbTopology.SIZE + x;
                    int cell = index % PcbTopology.CELLS;
                    if (occupied[cell] >= 0 || PcbTopology.cut(tag, cell)
                            || index != i && PcbTopology.get(tag, index / PcbTopology.CELLS, cell) != PcbTopology.EMPTY) return false;
                    occupied[cell] = i;
                }
            }
        }
        return true;
    }
}
