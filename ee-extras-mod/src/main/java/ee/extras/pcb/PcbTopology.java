package ee.extras.pcb;

import java.util.ArrayDeque;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Компактная топология проекта: 16×16 ячеек, до трёх слоёв. Значения — Cell. */
public final class PcbTopology {
    public static final int SIZE = 16;
    public static final int CELLS = SIZE * SIZE;
    public static final int MAX_LAYERS = 3;
    public static final int VERSION = 1;

    public static final int EMPTY = 0;
    public static final int TRACE = 1;
    public static final int COMPONENT = 2;
    public static final int VIA = 3;
    public static final int TERMINAL = 4;

    private PcbTopology() {}

    public static CompoundTag create(int level) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Level", level);
        tag.putInt("Version", VERSION);
        tag.putByteArray("Cells", new byte[CELLS * MAX_LAYERS]);
        return tag;
    }

    public static CompoundTag normalize(CompoundTag source, int fallbackLevel) {
        int level = Math.max(1, Math.min(MAX_LAYERS, source.getInt("Level")));
        if (!source.contains("Level")) level = fallbackLevel;
        CompoundTag result = create(level);
        byte[] old = source.getByteArray("Cells");
        byte[] cells = Arrays.copyOf(old, CELLS * MAX_LAYERS);
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] < EMPTY || cells[i] > TERMINAL) cells[i] = EMPTY;
        }
        result.putByteArray("Cells", cells);
        return result;
    }

    public static int level(CompoundTag tag) {
        return Math.max(1, Math.min(MAX_LAYERS, tag.contains("Level") ? tag.getInt("Level") : 1));
    }

    public static int get(CompoundTag tag, int layer, int cell) {
        byte[] cells = tag.getByteArray("Cells");
        int index = layer * CELLS + cell;
        return index >= 0 && index < cells.length ? cells[index] : EMPTY;
    }

    public static void set(CompoundTag tag, int layer, int cell, int value) {
        byte[] cells = Arrays.copyOf(tag.getByteArray("Cells"), CELLS * MAX_LAYERS);
        cells[layer * CELLS + cell] = (byte) value;
        tag.putByteArray("Cells", cells);
        tag.putInt("Level", level(tag));
        tag.putInt("Version", VERSION);
    }

    public static void setLevel(CompoundTag tag, int level) {
        CompoundTag normalized = normalize(tag, level);
        normalized.putInt("Level", level);
        byte[] cells = normalized.getByteArray("Cells");
        for (int layer = level; layer < MAX_LAYERS; layer++) {
            Arrays.fill(cells, layer * CELLS, (layer + 1) * CELLS, (byte) EMPTY);
        }
        if (level == 1) {
            for (int cell = 0; cell < CELLS; cell++) {
                if (cells[cell] == VIA) cells[cell] = TRACE;
            }
        }
        normalized.putByteArray("Cells", cells);
        copyInto(normalized, tag);
    }

    public static void copyInto(CompoundTag source, CompoundTag target) {
        target.putInt("Level", level(source));
        target.putInt("Version", VERSION);
        target.putByteArray("Cells", source.getByteArray("Cells").clone());
    }

    public static void attach(ItemStack stack, CompoundTag topology) {
        CompoundTag copy = normalize(topology, level(topology));
        copy.putBoolean("Validated", true);
        stack.set(PcbItems.PCB_TOPOLOGY.get(), copy);
    }

    /** Ошибка — русский текст для журнала/подсказки; null — проект пригоден к производству. */
    public static String validate(CompoundTag source) {
        CompoundTag tag = normalize(source, 1);
        int level = level(tag);
        int components = 0;
        int terminals = 0;
        int occupied = 0;
        int firstTerminal = -1;
        for (int layer = 0; layer < level; layer++) {
            for (int cell = 0; cell < CELLS; cell++) {
                int value = get(tag, layer, cell);
                if (value == EMPTY) continue;
                occupied++;
                if (value == COMPONENT) components++;
                if (value == TERMINAL) {
                    terminals++;
                    int x = cell % SIZE;
                    int y = cell / SIZE;
                    if (layer != 0 || (x != 0 && y != 0 && x != SIZE - 1 && y != SIZE - 1))
                        return "Выводы должны быть на краю нижнего слоя";
                    if (firstTerminal < 0) firstTerminal = index(layer, cell);
                }
            }
        }
        int required = level == 1 ? 2 : level == 2 ? 4 : 8;
        if (components < required) return "Нужно компонентов: не менее " + required;
        if (terminals < 2) return "Нужно не менее двух выводов на краю платы";
        if (occupied == 0) return "Пустой проект";

        boolean[] reached = new boolean[CELLS * MAX_LAYERS];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        reached[firstTerminal] = true;
        queue.add(firstTerminal);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int layer = current / CELLS;
            int cell = current % CELLS;
            int x = cell % SIZE;
            int y = cell / SIZE;
            if (x > 0) visit(tag, reached, queue, index(layer, cell - 1));
            if (x + 1 < SIZE) visit(tag, reached, queue, index(layer, cell + 1));
            if (y > 0) visit(tag, reached, queue, index(layer, cell - SIZE));
            if (y + 1 < SIZE) visit(tag, reached, queue, index(layer, cell + SIZE));
            if (get(tag, layer, cell) == VIA) {
                for (int other = 0; other < level; other++) {
                    if (other != layer && get(tag, other, cell) != EMPTY) visit(tag, reached, queue, index(other, cell));
                }
            }
        }
        for (int layer = 0; layer < level; layer++) {
            for (int cell = 0; cell < CELLS; cell++) {
                int value = get(tag, layer, cell);
                if (value != EMPTY && !reached[index(layer, cell)]) return "Есть обрыв или неподключённый вывод";
            }
        }
        return null;
    }

    private static void visit(CompoundTag tag, boolean[] reached, ArrayDeque<Integer> queue, int index) {
        int layer = index / CELLS;
        int cell = index % CELLS;
        if (!reached[index] && get(tag, layer, cell) != EMPTY) {
            reached[index] = true;
            queue.addLast(index);
        }
    }

    private static int index(int layer, int cell) {
        return layer * CELLS + cell;
    }
}
