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
    public static final int VERSION = 3;
    static final String UNSUPPORTED = "Старый формат проекта: очистите редактор и соберите схему заново";

    public static final int EMPTY = 0;
    public static final int TRACE = 1;
    public static final int COMPONENT = 2;
    public static final int VIA = 3;
    public static final int TERMINAL = 4;

    static final String EDGES = "TraceCuts";

    static int neighbor(int cell, int direction) {
        int x = cell % SIZE, y = cell / SIZE;
        return switch (direction) {
            case 0 -> x > 0 ? cell - 1 : -1;
            case 1 -> x < SIZE - 1 ? cell + 1 : -1;
            case 2 -> y > 0 ? cell - SIZE : -1;
            case 3 -> y < SIZE - 1 ? cell + SIZE : -1;
            default -> -1;
        };
    }

    static boolean edgeCut(CompoundTag tag, int layer, int cell, int direction) {
        byte[] edges = tag.getByteArray(EDGES);
        int index = layer * CELLS + cell;
        return index >= 0 && index < edges.length && (edges[index] & (1 << direction)) != 0;
    }

    static boolean connected(CompoundTag tag, int layer, int first, int second) {
        for (int direction = 0; direction < 4; direction++) {
            if (neighbor(first, direction) == second)
                return !edgeCut(tag, layer, first, direction) && !edgeCut(tag, layer, second, direction ^ 1);
        }
        return false;
    }

    static void setEdgeCut(CompoundTag tag, int layer, int cell, int direction, boolean blocked) {
        int next = neighbor(cell, direction);
        if (layer < 0 || layer >= MAX_LAYERS || next < 0) return;
        byte[] edges = Arrays.copyOf(tag.getByteArray(EDGES), CELLS * MAX_LAYERS);
        int index = layer * CELLS + cell, other = layer * CELLS + next;
        edges[index] = (byte) (blocked ? edges[index] | (1 << direction) : edges[index] & ~(1 << direction));
        edges[other] = (byte) (blocked ? edges[other] | (1 << (direction ^ 1)) : edges[other] & ~(1 << (direction ^ 1)));
        tag.putByteArray(EDGES, edges);
    }

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
        result.putInt("Version", source.isEmpty() ? VERSION : source.getInt("Version"));
        byte[] old = source.getByteArray("Cells");
        byte[] cells = Arrays.copyOf(old, CELLS * MAX_LAYERS);
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] < EMPTY || cells[i] > TERMINAL || cut(source, i % CELLS)) cells[i] = EMPTY;
        }
        result.putByteArray("Cells", cells);
        if (source.contains(EDGES)) {
            byte[] edges = Arrays.copyOf(source.getByteArray(EDGES), CELLS * MAX_LAYERS);
            for (int i = 0; i < edges.length; i++) edges[i] = i >= level * CELLS ? 0 : (byte) (edges[i] & 15);
            result.putByteArray(EDGES, edges);
        }
        if (source.contains("Cut")) result.putByteArray("Cut", Arrays.copyOf(source.getByteArray("Cut"), CELLS));
        if (source.contains(PcbWiring.KEY)) {
            int[] parts = Arrays.copyOf(source.getIntArray(PcbWiring.KEY), CELLS * MAX_LAYERS);
            for (int i = 0; i < parts.length; i++) {
                if (cells[i] != COMPONENT || i >= level * CELLS) parts[i] = 0;
            }
            result.putIntArray(PcbWiring.KEY, parts);
        }
        if (source.contains(PcbParameters.KEY)) {
            CompoundTag parameters = source.getCompound(PcbParameters.KEY).copy();
            int[] definitions = result.getIntArray(PcbWiring.KEY);
            for (String key : java.util.Set.copyOf(parameters.getAllKeys())) {
                try {
                    int index = Integer.parseInt(key);
                    if (index < 0 || index >= definitions.length || definitions[index] == 0) parameters.remove(key);
                } catch (NumberFormatException ignored) { parameters.remove(key); }
            }
            if (!parameters.isEmpty()) result.put(PcbParameters.KEY, parameters);
        }
        return result;
    }

    static boolean positionedContacts(CompoundTag tag) {
        return tag != null && tag.getInt("Version") >= VERSION;
    }

    static int[] contacts(CompoundTag tag) {
        byte[] cells = tag.getByteArray("Cells");
        return java.util.stream.IntStream.range(0, Math.min(CELLS, cells.length))
                .filter(cell -> cells[cell] == TERMINAL).toArray();
    }

    static boolean cut(CompoundTag tag, int cell) {
        byte[] cut = tag.getByteArray("Cut");
        return cell < cut.length && cut[cell] != 0;
    }

    static void toggleCut(CompoundTag tag, int cell) {
        byte[] cut = Arrays.copyOf(tag.getByteArray("Cut"), CELLS);
        cut[cell] = (byte) (cut[cell] == 0 ? 1 : 0);
        tag.putByteArray("Cut", cut);
        for (int layer = 0; layer < MAX_LAYERS; layer++) set(tag, layer, cell, EMPTY);
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
        if (tag.contains(PcbWiring.KEY)) setPart(tag, layer, cell, 0);
        if (value == EMPTY) for (int direction = 0; direction < 4; direction++) setEdgeCut(tag, layer, cell, direction, false);
    }

    static void setPart(CompoundTag tag, int layer, int cell, int definition) {
        int[] parts = Arrays.copyOf(tag.getIntArray(PcbWiring.KEY), CELLS * MAX_LAYERS);
        parts[layer * CELLS + cell] = definition;
        if (definition == 0) PcbParameters.put(tag, layer * CELLS + cell, new CompoundTag());
        tag.putIntArray(PcbWiring.KEY, parts);
        if (definition > 0 && PcbWiring.valid(definition)) PcbFootprint.isolatePins(tag, layer, cell, definition);
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
        copyInto(normalize(normalized, level), tag);
    }

    public static void copyInto(CompoundTag source, CompoundTag target) {
        if (source.contains(PcbParameters.KEY)) target.put(PcbParameters.KEY, source.getCompound(PcbParameters.KEY).copy());
        else target.remove(PcbParameters.KEY);
        target.putInt("Level", level(source));
        target.putInt("Version", source.getInt("Version"));
        target.putByteArray("Cells", source.getByteArray("Cells").clone());
        if (source.contains(EDGES)) target.putByteArray(EDGES, source.getByteArray(EDGES).clone());
        else target.remove(EDGES);
        if (source.contains("Cut")) target.putByteArray("Cut", source.getByteArray("Cut").clone());
        else target.remove("Cut");
        if (source.contains(PcbWiring.KEY)) target.putIntArray(PcbWiring.KEY, source.getIntArray(PcbWiring.KEY).clone());
        else target.remove(PcbWiring.KEY);
    }

    public static void attach(ItemStack stack, CompoundTag topology) {
        CompoundTag copy = normalize(topology, level(topology));
        copy.putBoolean("Validated", true);
        stack.set(PcbItems.PCB_TOPOLOGY.get(), copy);
    }

    /** Ошибка — русский текст для журнала/подсказки; null — проект пригоден к производству. */
    public static String validate(CompoundTag source) {
        if (source.getInt("Version") != VERSION) return UNSUPPORTED;
        if (!PcbParameters.valid(source)) return PcbParameters.ERROR;
        CompoundTag tag = normalize(source, 1);
        if (PcbWiring.active(tag)) return PcbWiring.compile(tag) == null ? PcbWiring.ERROR : null;
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
            if (x > 0 && connected(tag, layer, cell, cell - 1)) visit(tag, reached, queue, index(layer, cell - 1));
            if (x + 1 < SIZE && connected(tag, layer, cell, cell + 1)) visit(tag, reached, queue, index(layer, cell + 1));
            if (y > 0 && connected(tag, layer, cell, cell - SIZE)) visit(tag, reached, queue, index(layer, cell - SIZE));
            if (y + 1 < SIZE && connected(tag, layer, cell, cell + SIZE)) visit(tag, reached, queue, index(layer, cell + SIZE));
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
