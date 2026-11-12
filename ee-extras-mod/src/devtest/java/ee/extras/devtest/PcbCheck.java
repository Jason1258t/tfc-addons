package ee.extras.devtest;

import ee.extras.pcb.PcbTopology;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

/**
 * Проверка проектов плат без запуска игры: ./gradlew pcbCheck [-Ppcb=имя|файл.nbt|файл.snbt].
 * Вердикт дают только штатные PcbTopology.validate и PcbWiring.compile; карта, цепи выводов и пояснения
 * PcbDiagnostics лишь показывают, где искать. Электрические и клиентские испытания на стенде остаются обязательными.
 */
public final class PcbCheck {
    record Demo(String name, Supplier<CompoundTag> build) {}
    record Result(String name, boolean ok, String text) {}

    /** Все проекты демонстраций и проверок, которые стенд собирает из кода. */
    static List<Demo> demos() {
        var list = new ArrayList<Demo>();
        list.add(new Demo("connector-tx", () -> PcbConnectorTest.design(false)));
        list.add(new Demo("connector-rx", () -> PcbConnectorTest.design(true)));
        list.add(new Demo("connector-supply", PcbConnectorTest::supplyDesign));
        list.add(new Demo("regulator-demo", () -> PcbRegulatorDemo.topology(true)));
        list.add(new Demo("regulator-demo-nocap", () -> PcbRegulatorDemo.topology(false)));
        list.add(new Demo("bridge-demo", () -> PcbBridgeDemo.topology(true)));
        list.add(new Demo("bridge-demo-nocap", () -> PcbBridgeDemo.topology(false)));
        list.add(new Demo("opto", PcbOptoTest::topology));
        list.add(new Demo("sensor-temperature", () -> PcbSensorTest.topology(true)));
        list.add(new Demo("sensor-light", () -> PcbSensorTest.topology(false)));
        for (int rotation = 0; rotation < 4; rotation++) for (boolean mirror : new boolean[] {false, true}) {
            int r = rotation;
            String suffix = "-r" + r + (mirror ? "m" : "");
            list.add(new Demo("bridge" + suffix, () -> PcbBridgeTest.topology(r, mirror)));
            list.add(new Demo("regulator" + suffix, () -> PcbRegulatorTest.topology(r, mirror)));
            list.add(new Demo("stepup" + suffix, () -> PcbStepUpTest.topology(r, mirror)));
        }
        return list;
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "build/pcb-check");
        String filter = args.length > 1 ? args[1] : "";
        Files.createDirectories(out);
        var results = new ArrayList<Result>();
        if (!filter.isEmpty() && Files.isRegularFile(Path.of(filter))) {
            Path file = Path.of(filter);
            String name = file.getFileName().toString().replaceFirst("\\.s?nbt$", "");
            results.add(check(name, () -> read(file), out));
        } else {
            for (var demo : demos()) if (filter.isEmpty() || demo.name().contains(filter)) results.add(check(demo.name(), demo.build(), out));
            if (results.isEmpty()) throw new IllegalArgumentException("Нет проекта или файла «" + filter + "». Проекты: "
                    + demos().stream().map(Demo::name).toList());
        }
        var summary = new StringBuilder();
        for (var r : results) summary.append(r.ok() ? "OK    " : "ОШИБКА").append(' ').append(r.name()).append('\n');
        long failed = results.stream().filter(r -> !r.ok()).count();
        summary.append("Проверено ").append(results.size()).append(", ошибок ").append(failed)
                .append(". Отчёты и проверенные NBT: ").append(out.toAbsolutePath()).append('\n');
        if (results.size() == 1) System.out.println(results.get(0).text());
        System.out.print(summary);
        Files.writeString(out.resolve("summary.txt"), summary);
        if (failed > 0) System.exit(1);
    }

    static Result check(String name, Supplier<CompoundTag> build, Path out) throws IOException {
        var text = new StringBuilder("Проект ").append(name).append('\n');
        CompoundTag tag;
        try {
            tag = build.get();
        } catch (RuntimeException e) {
            // Построитель демонстрации сам проверяет корпуса и разводку; его отказ — ошибка проекта.
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            text.append("ОШИБКА построения: ").append(cause.getMessage() != null ? cause.getMessage() : cause.toString()).append('\n');
            return write(new Result(name, false, text.toString()), null, out);
        }
        boolean ok = analyze(tag, text);
        return write(new Result(name, ok, text.toString()), ok ? PcbTopology.normalize(tag, PcbTopology.level(tag)) : null, out);
    }

    private static Result write(Result result, CompoundTag tag, Path out) throws IOException {
        Files.writeString(out.resolve(result.name() + ".txt"), result.text());
        Path nbt = out.resolve(result.name() + ".nbt"), snbt = out.resolve(result.name() + ".snbt");
        if (tag != null) {
            NbtIo.writeCompressed(tag, nbt);
            Files.writeString(snbt, tag.toString());
        } else {
            Files.deleteIfExists(nbt);
            Files.deleteIfExists(snbt);
        }
        return result;
    }

    /** true — проект принят штатными validate и compile. */
    static boolean analyze(CompoundTag source, StringBuilder text) {
        int level = PcbTopology.level(source);
        String error = PcbTopology.validate(source);
        // Плата получает проект через PcbTopology.attach, то есть нормализованным; так же его и компилируем.
        CompoundTag tag = PcbTopology.normalize(source, level);
        List<?> parts = error != null ? null : PcbModulesTest.call(PcbBridgeTest.wiring(), null, "compile", tag);
        boolean ok = error == null && parts != null;
        text.append("Уровень L").append(level).append(", validate: ").append(error == null ? "OK" : error)
                .append(", compile: ").append(parts == null ? "нет схемы" : parts.size() + " элементов").append('\n');
        long dropped = Arrays.stream(source.getIntArray("ElectricalParts")).filter(d -> d != 0).count()
                - Arrays.stream(tag.getIntArray("ElectricalParts")).filter(d -> d != 0).count();
        if (dropped > 0) text.append("! При сохранении отброшено модулей без клетки корпуса: ").append(dropped).append('\n');

        int[] groups = groups(tag);
        int[] definitions = Arrays.copyOf(tag.getIntArray("ElectricalParts"), PcbTopology.MAX_LAYERS * PcbTopology.CELLS);
        var issues = new ArrayList<int[]>();
        var messages = new ArrayList<String>();
        for (Object issue : PcbModulesTest.<List<?>>call(type("PcbDiagnostics"), null, "check", tag)) {
            int layer = PcbModulesTest.call(issue.getClass(), issue, "layer");
            int cell = PcbModulesTest.call(issue.getClass(), issue, "cell");
            String message = PcbModulesTest.call(issue.getClass(), issue, "message");
            issues.add(new int[] {layer, cell});
            messages.add((cell < 0 ? "" : "[" + xy(cell) + (layer > 0 ? " слой " + (layer + 1) : "") + "] ") + message);
        }
        if (!messages.isEmpty()) {
            text.append("Пояснения PcbDiagnostics:\n");
            for (String message : messages) text.append("  ! ").append(message).append('\n');
        }

        // Модули: буква на карте, корпус, выводы и их цепи
        var letters = new HashMap<Integer, Character>();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        text.append("Модули:\n");
        int warnings = 0;
        for (int index = 0; index < definitions.length; index++) {
            int definition = definitions[index];
            if (definition == 0) continue;
            char letter = alphabet.charAt(letters.size() % alphabet.length());
            letters.put(index, letter);
            if (!PcbModulesTest.<Boolean>call(PcbBridgeTest.wiring(), null, "valid", definition)) {
                text.append("  ").append(letter).append(' ').append(xy(index % PcbTopology.CELLS)).append(" неизвестный модуль ").append(definition).append('\n');
                continue;
            }
            Object module = PcbModulesTest.call(type("PcbModules"), null, "get", definition);
            String moduleName = PcbModulesTest.call(module.getClass(), module, "name");
            int pins = PcbModulesTest.call(module.getClass(), module, "pins");
            int rotation = PcbModulesTest.call(PcbBridgeTest.wiring(), null, "rotation", definition);
            boolean mirrored = PcbModulesTest.call(PcbBridgeTest.wiring(), null, "mirrored", definition);
            int width = PcbModulesTest.call(type("PcbFootprint"), null, "width", definition);
            int height = PcbModulesTest.call(type("PcbFootprint"), null, "height", definition);
            text.append("  ").append(letter).append(' ').append(moduleName).append(" — угол ").append(xy(index % PcbTopology.CELLS))
                    .append(", ").append(width).append('×').append(height).append(", поворот ").append(rotation * 90).append('°')
                    .append(mirrored ? ", отражён" : "").append('\n');
            var byGroup = new LinkedHashMap<Integer, List<String>>();
            for (int pin = 0; pin < pins; pin++) {
                String pinName = PcbModulesTest.call(module.getClass(), module, "pinName", pin);
                int cell = PcbModulesTest.call(PcbBridgeTest.wiring(), null, "pinCell", index, definition, pin);
                String where;
                if (cell < 0) where = "за краем платы";
                else if (groups[cell] < 0) where = xy(cell % PcbTopology.CELLS) + ", не подключён";
                else {
                    where = xy(cell % PcbTopology.CELLS) + ", цепь " + groups[cell];
                    byGroup.computeIfAbsent(groups[cell], g -> new ArrayList<>()).add(pinName);
                }
                text.append("      ").append(pin).append(' ').append(pinName).append(": ").append(where).append('\n');
            }
            for (var shared : byGroup.values()) {
                if (shared.size() < 2) continue;
                warnings++;
                text.append("      ⚠ в одной цепи: ").append(String.join(", ", shared)).append('\n');
            }
        }
        if (warnings > 0) text.append("⚠ — выводы одного модуля соединены дорожками; проверьте, что так задумано.\n");

        text.append("Внешние выводы платы:");
        for (int cell = 0; cell < PcbTopology.CELLS; cell++)
            if (PcbTopology.get(tag, 0, cell) == PcbTopology.TERMINAL) text.append(' ').append(xy(cell)).append(" цепь ").append(groups[cell]).append(';');
        text.append('\n');
        map(tag, level, definitions, letters, groups, issues, text);
        text.append(ok ? "ИТОГ: проект принят штатной проверкой\n" : "ИТОГ: проект НЕ принят\n");
        return ok;
    }

    /**
     * Цепи по тем же правилам, что и PcbWiring.compile: проводники, PcbTopology.connected между соседями,
     * переходное отверстие соединяет проводники всех слоёв клетки. Только для карты и списка выводов.
     */
    static int[] groups(CompoundTag tag) {
        int size = PcbTopology.level(tag) * PcbTopology.CELLS;
        int[] parent = new int[PcbTopology.MAX_LAYERS * PcbTopology.CELLS];
        Arrays.fill(parent, -1);
        for (int i = 0; i < size; i++) if (conductor(PcbTopology.get(tag, i / PcbTopology.CELLS, i % PcbTopology.CELLS))) parent[i] = i;
        for (int i = 0; i < size; i++) {
            if (parent[i] < 0) continue;
            int layer = i / PcbTopology.CELLS, cell = i % PcbTopology.CELLS;
            if (cell % PcbTopology.SIZE > 0 && parent[i - 1] >= 0 && connected(tag, layer, cell, cell - 1)) join(parent, i, i - 1);
            if (cell >= PcbTopology.SIZE && parent[i - PcbTopology.SIZE] >= 0 && connected(tag, layer, cell, cell - PcbTopology.SIZE)) join(parent, i, i - PcbTopology.SIZE);
            if (PcbTopology.get(tag, layer, cell) == PcbTopology.VIA)
                for (int j = cell; j < size; j += PcbTopology.CELLS) if (parent[j] >= 0) join(parent, i, j);
        }
        int[] numbers = new int[parent.length];
        Arrays.fill(numbers, -1);
        var ids = new HashMap<Integer, Integer>();
        for (int i = 0; i < size; i++) if (parent[i] >= 0) numbers[i] = ids.computeIfAbsent(root(parent, i), k -> ids.size() + 1);
        return numbers;
    }

    private static void map(CompoundTag tag, int level, int[] definitions, Map<Integer, Character> letters, int[] groups,
                            List<int[]> issues, StringBuilder text) {
        text.append("Карта (буква — корпус модуля, + дорожка, o переход, T вывод платы, x вырез, ! место ошибки):\n   ");
        for (int layer = 0; layer < level; layer++) {
            text.append(String.format("%-20s", "слой " + (layer + 1)));
        }
        text.append("\n   ");
        for (int layer = 0; layer < level; layer++) text.append("0123456789ABCDEF    ");
        text.append('\n');
        for (int y = 0; y < PcbTopology.SIZE; y++) {
            text.append(String.format("%X  ", y));
            for (int layer = 0; layer < level; layer++) {
                for (int x = 0; x < PcbTopology.SIZE; x++) {
                    int cell = y * PcbTopology.SIZE + x;
                    char c = switch (PcbTopology.get(tag, layer, cell)) {
                        case PcbTopology.TRACE -> '+';
                        case PcbTopology.VIA -> 'o';
                        case PcbTopology.TERMINAL -> 'T';
                        default -> '.';
                    };
                    int owner = PcbModulesTest.call(type("PcbFootprint"), null, "owner", definitions, layer, cell);
                    if (owner >= 0 && letters.containsKey(owner)) c = letters.get(owner);
                    if (cut(tag, cell)) c = 'x';
                    for (int[] issue : issues) if (issue[1] == cell && (issue[0] == layer || issue[0] < 0)) c = '!';
                    text.append(c);
                }
                text.append("    ");
            }
            text.append('\n');
        }
    }

    static CompoundTag read(Path file) {
        try {
            CompoundTag root;
            if (file.toString().endsWith(".snbt")) root = TagParser.parseTag(Files.readString(file));
            else {
                try {
                    root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                } catch (IOException e) {
                    root = NbtIo.read(file);
                }
            }
            CompoundTag topology = find(root);
            if (topology == null) throw new IllegalStateException("В файле нет топологии платы (тега с Cells)");
            return topology;
        } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException("Не читается " + file + ": " + e.getMessage(), e);
        }
    }

    /** Топология может лежать внутри NBT блока, предмета или чертежа — ищем первый тег с Cells. */
    private static CompoundTag find(CompoundTag tag) {
        if (tag == null) return null;
        if (tag.contains("Cells", Tag.TAG_BYTE_ARRAY)) return tag;
        for (String key : tag.getAllKeys()) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound && find(compound) != null) return find(compound);
            if (child instanceof net.minecraft.nbt.ListTag list)
                for (Tag item : list) if (item instanceof CompoundTag compound && find(compound) != null) return find(compound);
        }
        return null;
    }

    private static boolean conductor(int value) {
        return value == PcbTopology.TRACE || value == PcbTopology.VIA || value == PcbTopology.TERMINAL;
    }

    private static boolean connected(CompoundTag tag, int layer, int first, int second) {
        return PcbModulesTest.call(PcbTopology.class, null, "connected", tag, layer, first, second);
    }

    private static boolean cut(CompoundTag tag, int cell) {
        return PcbModulesTest.call(PcbTopology.class, null, "cut", tag, cell);
    }

    private static Class<?> type(String name) {
        try { return Class.forName("ee.extras.pcb." + name); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    private static String xy(int cell) {
        return String.format("%X,%X", cell % PcbTopology.SIZE, cell / PcbTopology.SIZE);
    }

    private static int root(int[] parent, int i) {
        while (i != parent[i]) i = parent[i] = parent[parent[i]];
        return i;
    }

    private static void join(int[] parent, int a, int b) {
        parent[root(parent, a)] = root(parent, b);
    }

    private PcbCheck() {}
}
