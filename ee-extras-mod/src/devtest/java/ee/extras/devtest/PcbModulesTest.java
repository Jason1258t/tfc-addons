package ee.extras.devtest;

import ee.extras.pcb.*;
import net.minecraft.nbt.CompoundTag;

import com.mojang.brigadier.CommandDispatcher;
import java.util.HashSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Проверяет монтажную геометрию и действия штатного серверного меню, а не копию алгоритма. */
public final class PcbModulesTest {
    private static final BlockPos KNOB = new BlockPos(24, -60, 24);
    private static final BlockPos SUPPLY = KNOB.east(4);
    private PcbModulesTest() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcbmodules").requires(source -> source.hasPermission(2))
                .executes(context -> run(context.getSource())));
        dispatcher.register(Commands.literal("pcbknobs").requires(source -> source.hasPermission(2))
                .then(Commands.literal("pot").executes(c -> knobBuild(c.getSource(), true)))
                .then(Commands.literal("rheostat").executes(c -> knobBuild(c.getSource(), false)))
                .then(Commands.literal("set").then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99))
                        .executes(c -> knobSet(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "percent")))))
                .then(Commands.literal("expect").then(Commands.argument("volts", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 24))
                        .executes(c -> knobExpect(c.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "volts")))))
                .then(Commands.literal("reload").executes(c -> {
                    var device = knob(c.getSource().getLevel());
                    var saved = new CompoundTag(); device.write(saved); device.read(saved);
                    return 1;
                })));

    }

    private static PcbCircuitDevice knob(ServerLevel level) {
        return com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level).getDevice(KNOB, PcbCircuitDevice.class);
    }

    private static int knobBuild(CommandSourceStack source, boolean pot) {
        var level = source.getLevel();
        DevTest.place(level, KNOB, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        DevTest.place(level, SUPPLY, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        DevTest.place(level, KNOB, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM, true));
        DevTest.place(level, SUPPLY, com.george_vi.electroenergetics.CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        var tag = PcbTopology.create(1);
        PcbTopology.set(tag, 0, 0, PcbTopology.TERMINAL);
        PcbTopology.set(tag, 0, 15, PcbTopology.TERMINAL);
        PcbTopology.set(tag, 0, 85, PcbTopology.COMPONENT);
        call(PcbTopology.class, null, "setPart", tag, 0, 85, PcbWiring.component(pot ? 15 : 14, 0, 5, 3, false));
        var stack = PcbItems.L1_BOARD.toStack();
        PcbTopology.attach(stack, tag);
        call(PcbCircuitBlockEntity.class, level.getBlockEntity(KNOB), "setBoard", stack);
        var kind = pot ? PcbCircuitDevice.Kind.POTENTIOMETER : PcbCircuitDevice.Kind.RHEOSTAT;
        var parts = new java.util.ArrayList<PcbCircuitDevice.Part>();
        parts.add(new PcbCircuitDevice.Part(kind, 0, pot ? 1 : 2, 1000, 2, 0, 24));
        if (!pot) parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR, 2, 1, 1000, 0, 0, 24));
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER, 2, 1, 1e9, 0, 0, 24));
        try {
            var setter = PcbCircuitDevice.class.getDeclaredMethod("setCircuit", java.util.List.class, PcbCircuitDevice.Rating.class, int.class);
            setter.setAccessible(true); setter.invoke(knob(level), parts, PcbCircuitDevice.Rating.L1, 2);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        DevTest.wire(level, SUPPLY, 0, KNOB, 0);
        DevTest.wire(level, SUPPLY, 1, KNOB, 1);
        var battery = com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level)
                .getDevice(SUPPLY, com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.class);
        var entity = (com.simibubi.create.foundation.blockEntity.SmartBlockEntity) level.getBlockEntity(SUPPLY);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(24000);
        battery.voltage = 24; battery.acFrequency = 0;
        return 1;
    }

    private static int potentiometerHeight() { return PcbFootprint.height(definition(15, 0)); }

    private static int knobSet(CommandSourceStack source, int value) {
        var level = source.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var circuit = new CompoundTag(); knob(level).write(circuit);
        boolean potentiometer = circuit.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getString("Kind").equals("POTENTIOMETER");
        var local = new net.minecraft.world.phys.Vec3(6.0 / 16, 2.0 / 16, (5 + (potentiometer ? potentiometerHeight() : 4) * value / 100.0) / 16);
        net.minecraft.world.phys.Vec3 mounted = call(PcbCircuitBlock.class, null, "mountedPoint", local, net.minecraft.core.Direction.UP);
        var hit = new net.minecraft.world.phys.BlockHitResult(mounted.add(net.minecraft.world.phys.Vec3.atLowerCornerOf(KNOB)), net.minecraft.core.Direction.UP, KNOB, false);
        check(level.getBlockState(KNOB).useWithoutItem(level, player, hit).consumesAction(), "ползунок принимает клик");
        var saved = new CompoundTag(); knob(level).write(saved);
        check(saved.getIntArray("Positions")[0] == value, "клик задаёт точное положение " + value);
        return 1;
    }

    private static int knobExpect(CommandSourceStack source, double expected) {
        var saved = new CompoundTag(); knob(source.getLevel()).write(saved);
        double value = saved.getDouble("Meter");
        check(!saved.getBoolean("Burnt") && Math.abs(value - expected) < 0.12, "напряжение " + value + " вместо " + expected);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Ползунок: %.3f В (ожидалось %.3f В), положение %d%%", value, expected, saved.getIntArray("Positions")[0])), false);
        return 1;
    }

    static int modulePins(int definition) {
        Object module = call(internal("PcbModules"), null, "get", definition);
        return call(module.getClass(), module, "pins");
    }

    static int definition(int type, int rotation) { return PcbWiring.component(type, rotation, 5, 0, false); }

    @SuppressWarnings("unchecked")
    static <T> T call(Class<?> type, Object receiver, String name, Object... args) {
        try {
            Class<?>[] signature = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) signature[i] = args[i] instanceof Integer ? int.class
                    : args[i] instanceof Boolean ? boolean.class : args[i] instanceof java.util.List<?> ? java.util.List.class : args[i].getClass();
            var method = type.getDeclaredMethod(name, signature);
            method.setAccessible(true);
            return (T) method.invoke(receiver, args);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static Class<?> internal(String name) {
        try { return Class.forName("ee.extras.pcb." + name); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    private static final class PcbFootprint {
        static final Class<?> TYPE = internal("PcbFootprint");
        static boolean canPlace(CompoundTag tag, int layer, int anchor, int definition, int ignored) {
            return call(TYPE, null, "canPlace", tag, layer, anchor, definition, ignored);
        }
        static boolean validate(CompoundTag tag) { return call(TYPE, null, "validate", tag); }
        static int width(int definition) { return call(TYPE, null, "width", definition); }
        static int height(int definition) { return call(TYPE, null, "height", definition); }
        static int owner(CompoundTag tag, int layer, int cell) { return call(TYPE, null, "owner", tag, layer, cell); }
        static boolean contains(int anchor, int definition, int cell) { return call(TYPE, null, "contains", anchor, definition, cell); }
    }

    private static final class PcbWiring {
        static final Class<?> TYPE = internal("PcbWiring");
        static final String KEY = "ElectricalParts";
        static final PcbCircuitDevice.Kind[] KINDS = kinds();
        private static PcbCircuitDevice.Kind[] kinds() {
            try {
                var field = TYPE.getDeclaredField("KINDS");
                field.setAccessible(true);
                return (PcbCircuitDevice.Kind[]) field.get(null);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        static int component(int type, int rotation, int resistance, int threshold, boolean nc) {
            return call(TYPE, null, "component", type, rotation, resistance, threshold, nc);
        }
        static int minimumLevel(int type) { return call(TYPE, null, "minimumLevel", type); }
        static int pinCell(int anchor, int definition, int pin) { return call(TYPE, null, "pinCell", anchor, definition, pin); }
        static boolean active(CompoundTag tag) { return call(TYPE, null, "active", tag); }
        static java.util.List<PcbCircuitDevice.Part> compile(CompoundTag tag) { return call(TYPE, null, "compile", tag); }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("Платы: " + message);
    }

    private static int run(CommandSourceStack source) {
        int checks = 0;
        // Регистр3×2: девять сигналов и явные П/З помещаются без увеличения корпуса.
        for (int rotation=0;rotation<4;rotation++) {
            var pins=new HashSet<Integer>();
            int w=(rotation&1)==0?3:2, h=(rotation&1)==0?2:3;
            for (int pin=0;pin<11;pin++) {
                int cell=call(PcbFootprint.TYPE,null,"ringCell",85,3,2,rotation,pin,11);
                int x=cell%16-5, y=cell/16-5;
                check(cell>=0 && pins.add(cell) && (x<0 || x>=w || y<0 || y>=h),"11 отдельных выводов регистра3×2 с П/З");
            }
        }
        for (int type = 0; type < PcbWiring.KINDS.length; type++) {
            for (int rotation = 0; rotation < 4; rotation++) for (int mirrored = 0; mirrored < 2; mirrored++) {
                int definition = PcbWiring.component(type, rotation, 3, 0, false);
                if (mirrored != 0) definition = call(PcbWiring.TYPE, null, "mirror", definition);
                int twice = call(PcbWiring.TYPE, null, "mirror", (int) call(PcbWiring.TYPE, null, "mirror", definition));
                check(twice == definition, "двойное отражение сохраняет определение");
                var tag = PcbTopology.create(PcbWiring.minimumLevel(type));
                int anchor = 5 * 16 + 5;
                check(PcbFootprint.canPlace(tag, 0, anchor, definition, -1), "свободное место");
                PcbTopology.set(tag, 0, anchor, PcbTopology.COMPONENT);
                call(PcbTopology.class, null, "setPart", tag, 0, anchor, definition);
                check(PcbFootprint.validate(tag), "валидный корпус");
                HashSet<Integer> pins = new HashSet<>();
                for (int pin = 0; pin < modulePins(definition); pin++) {
                    int cell = PcbWiring.pinCell(anchor, definition, pin);
                    check(cell >= 0 && pins.add(cell) && !PcbFootprint.contains(anchor, definition, cell), "независимый вывод снаружи корпуса");
                    if (mirrored != 0) {
                        int original = PcbWiring.pinCell(anchor, (int) call(PcbWiring.TYPE, null, "mirror", definition), pin);
                        int px = original % 16 - 5, py = original / 16 - 5;
                        int expected = (rotation & 1) == 0 ? (5 + py) * 16 + 5 + PcbFootprint.width(definition) - 1 - px
                                : (5 + PcbFootprint.height(definition) - 1 - py) * 16 + 5 + px;
                        check(cell == expected, "отражённый вывод сохраняет имя и меняет координату");
                    }
                }
                int far = anchor + (PcbFootprint.height(definition) - 1) * 16 + PcbFootprint.width(definition) - 1;
                check(PcbFootprint.owner(tag, 0, far) == anchor, "выбор на дальнем краю");
                check(!PcbFootprint.canPlace(tag, 0, far, definition, -1), "запрет пересечения");
                check(!PcbFootprint.canPlace(tag, 0, 255, definition, -1)
                        || PcbFootprint.width(definition) == 1 && PcbFootprint.height(definition) == 1, "правый нижний край");
                var cut = tag.copy();
                byte[] cutCells = new byte[PcbTopology.CELLS];
                cutCells[far] = 1;
                cut.putByteArray("Cut", cutCells);
                check(!PcbFootprint.validate(cut), "вырез повреждает размещение");
                check(PcbFootprint.validate(PcbTopology.normalize(tag, 1)), "геометрия сохраняется после нормализации");
                checks++;
            }
        }
        var pinCuts = PcbTopology.create(2);
        PcbTopology.set(pinCuts, 0, 85, PcbTopology.COMPONENT);
        call(PcbTopology.class, null, "setPart", pinCuts, 0, 85, definition(25,0));
        call(PcbTopology.class, null, "setEdgeCut", pinCuts, 0, 0, 1, true);
        check(java.util.stream.IntStream.range(0, pinCuts.getByteArray("PinCuts").length).anyMatch(i -> pinCuts.getByteArray("PinCuts")[i] != 0), "плотный корпус изолирует соседние выводы");
        PcbTopology.set(pinCuts, 0, 85, PcbTopology.EMPTY);
        check(java.util.stream.IntStream.range(0, pinCuts.getByteArray("PinCuts").length).allMatch(i -> pinCuts.getByteArray("PinCuts")[i] == 0), "удаление убирает автоматические разрывы");
        check((boolean) call(PcbTopology.class, null, "edgeCut", pinCuts, 0, 0, 1), "ручной разрыв сохраняется отдельно");
        for (var face : net.minecraft.core.Direction.values()) for (int cell : new int[]{0,15,240,255}) {
            net.minecraft.world.phys.AABB bounds = call(PcbCircuitBlock.class, null, "contactBounds", cell, face);
            double[] dimensions = {bounds.getXsize(), bounds.getYsize(), bounds.getZsize()};
            java.util.Arrays.sort(dimensions);
            check(Math.abs(dimensions[0]-.75/16)<1e-9 && Math.abs(dimensions[1]-.75/16)<1e-9 && Math.abs(dimensions[2]-1.0/16)<1e-9, "обводка совпадает с площадкой на " + face);
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = new BlockPos(20, -60, 20);
        level.setBlockAndUpdate(pos, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        var editor = (PcbEditorBlockEntity) level.getBlockEntity(pos);
        var player = FakePlayerFactory.getMinecraft(level);
        var menu = new PcbEditorMenu(91, player.getInventory(), ContainerLevelAccess.create(level, pos), editor);
        menu.clickMenuButton(player, 1040 + 1);
        menu.clickMenuButton(player, 4 * 16 + 4);
        int tail = 5 * 16 + 5;
        menu.clickMenuButton(player, 2000 + tail);
        check((int) call(PcbEditorMenu.class, menu, "selected") == 4 * 16 + 4, "настройка по дальней клетке реле");
        menu.clickMenuButton(player, PcbEditorMenu.OHMS);
        check(((editor.topology().getIntArray(PcbWiring.KEY)[68] - 1) >>> 10 & 15) == 1, "настройка меняет установленный модуль");
        menu.clickMenuButton(player, PcbEditorMenu.ROTATE);
        check(PcbFootprint.width(editor.topology().getIntArray(PcbWiring.KEY)[68]) == 2, "поворот установленного модуля");
        menu.clickMenuButton(player, PcbEditorMenu.MIRROR);
        check((boolean) call(PcbWiring.TYPE, null, "mirrored", editor.topology().getIntArray(PcbWiring.KEY)[68]), "F отражает установленный корпус");
        menu.clickMenuButton(player, PcbEditorMenu.SELECT_TOOL + 7);
        menu.clickMenuButton(player, 5 * 16 + 5);
        menu.clickMenuButton(player, 8 * 16 + 8);
        check(editor.topology().getIntArray(PcbWiring.KEY)[68] == 0
                && PcbFootprint.owner(editor.topology(), 0, 9 * 16 + 9) == 136, "перенос по дальней клетке");
        menu.clickMenuButton(player, PcbEditorMenu.SELECT_TOOL + 4);
        menu.clickMenuButton(player, 9 * 16 + 9);
        check(!PcbWiring.active(editor.topology()), "ластик удаляет весь модуль");
        check(menu.clickMenuButton(player, PcbEditorMenu.UNDO), "отмена удаления");
        check(PcbFootprint.owner(editor.topology(), 0, 153) == 136, "отмена восстанавливает весь корпус");
        long restoredId = editor.topology().getCompound("ModuleIds").getLong("136");
        check(menu.clickMenuButton(player, PcbEditorMenu.UNDO), "отмена переноса");
        check(PcbFootprint.owner(editor.topology(), 0, 85) == 68, "отмена возвращает исходное место");
        check(menu.clickMenuButton(player, PcbEditorMenu.REDO), "повтор переноса");
        check(PcbFootprint.owner(editor.topology(), 0, 153) == 136, "повтор возвращает новое место");
        check(editor.topology().getCompound("ModuleIds").getLong("136") != restoredId, "история обновляет идентичность для защиты от старых пакетов");
        menu.clickMenuButton(player, PcbEditorMenu.SELECT_TOOL);
        menu.clickMenuButton(player, 200);
        check(!menu.clickMenuButton(player, PcbEditorMenu.REDO), "новая правка сбрасывает повтор");
        editor.setCell(0, 201, PcbTopology.TRACE);
        check(!menu.clickMenuButton(player, PcbEditorMenu.UNDO), "внешняя правка защищена от отмены");
        menu.clickMenuButton(player, 202);
        menu.clickMenuButton(player, 202);
        check(menu.clickMenuButton(player, PcbEditorMenu.UNDO), "неизменившееся действие не засоряет историю");
        check(PcbTopology.get(editor.topology(), 0, 202) == PcbTopology.EMPTY, "отмена реальной правки после повтора клика");
        menu.clickMenuButton(player, PcbEditorMenu.CLEAR);
        check(menu.clickMenuButton(player, PcbEditorMenu.UNDO), "очистка отменяется");
        check(PcbTopology.get(editor.topology(), 0, 201) == PcbTopology.TRACE, "очистка восстанавливает все слои проекта");
        menu.clickMenuButton(player, PcbEditorMenu.LEVEL);
        check(menu.clickMenuButton(player, PcbEditorMenu.UNDO) && menu.level() == 1, "отмена уровня синхронизирует меню");
        menu.clickMenuButton(player, PcbEditorMenu.CLEAR);
        for (int cell = 0; cell < 70; cell++) menu.clickMenuButton(player, cell);
        for (int step = 0; step < 64; step++) check(menu.clickMenuButton(player, PcbEditorMenu.UNDO), "ограниченная история " + step);
        check(!menu.clickMenuButton(player, PcbEditorMenu.UNDO), "история ограничена 64 шагами");
        source.sendSuccess(() -> Component.literal("История: отмена/повтор/удаление/перенос/очистка/уровень, защита от чужих правок и старых пакетов, лимит64 — пройдены"), false);
        PcbRegionTest.run(menu, editor, player);
        source.sendSuccess(() -> Component.literal("Участок платы: копия/перекрывающийся перенос/слои/контур/номинал/отражение/идентичность/разрывы/отмена — пройдены"), false);
        // Замкнутая трассировка: вывод — кнопка 2×2 — лампа 2×2 — вывод.
        editor.clear(1);
        menu = new PcbEditorMenu(92, player.getInventory(), ContainerLevelAccess.create(level, pos), editor);
        menu.clickMenuButton(player, 1040 + 2);
        menu.clickMenuButton(player, 4 * 16 + 3);
        menu.clickMenuButton(player, 1040 + 4);
        menu.clickMenuButton(player, 4 * 16 + 9);
        for (int x = 0; x < 16; x++) {
            if (x == 3 || x == 4 || x == 9 || x == 10) continue;
            editor.setCell(0, 4 * 16 + x, x == 0 || x == 15 ? PcbTopology.TERMINAL : PcbTopology.TRACE);
        }
        var parts = PcbWiring.compile(editor.topology());
        check(parts != null && parts.size() == 2, "компиляция двух многоклеточных модулей");
        var invalid = editor.topology().copy();
        PcbTopology.set(invalid, 0, 4 * 16 + 5, PcbTopology.EMPTY);
        check(PcbWiring.compile(invalid) == null, "реальный обрыв отвергнут");
        editor.setCell(0, 5 * 16 + 4, PcbTopology.TRACE);
        check(PcbTopology.get(editor.topology(), 0, 5 * 16 + 4) == PcbTopology.EMPTY, "дорожка не проходит через корпус");
        var old = editor.topology().copy();
        old.putInt("Version", 2);
        check(PcbWiring.compile(old) == null && PcbTopology.validate(old) != null, "старый формат не переосмысливается");
        var multilayer = PcbTopology.create(2);
        int definition = PcbWiring.component(2, 0, 0, 0, false);
        PcbTopology.set(multilayer, 0, 68, PcbTopology.COMPONENT);
        call(PcbTopology.class, null, "setPart", multilayer, 0, 68, definition);
        check(!PcbFootprint.canPlace(multilayer, 1, 68, definition, -1), "корпуса разных слоёв не пересекаются");
        PcbTopology.set(multilayer, 1, 68, PcbTopology.TRACE);
        check(PcbFootprint.validate(multilayer), "дорожка другого слоя проходит под корпусом");
        for (var face : net.minecraft.core.Direction.values()) {
            var boardPos = pos.offset(face.ordinal() * 2, 0, 4);
            var state = PcbItems.CIRCUIT_BLOCK.get().defaultBlockState()
                    .setValue(PcbCircuitBlock.FACING, face).setValue(PcbCircuitBlock.CUSTOM, true);
            level.setBlockAndUpdate(boardPos, state);
            var board = (PcbCircuitBlockEntity) level.getBlockEntity(boardPos);
            var stack = PcbItems.L1_BOARD.toStack();
            PcbTopology.attach(stack, editor.topology());
            call(PcbCircuitBlockEntity.class, board, "setBoard", stack);
            check((int) call(PcbCircuitBlockEntity.class, board, "controlAt", 84) == 67,
                    "дальний край кнопки на грани " + face);
            var point = new net.minecraft.world.phys.Vec3(4.5 / 16, 1.5 / 16, 5.5 / 16);
            net.minecraft.world.phys.Vec3 mounted = call(PcbCircuitBlock.class, null, "mountedPoint", point, face);
            check((int) call(PcbCircuitBlock.class, null, "hitCell", mounted, face) == 84,
                    "преобразование прицела на грани " + face);
            net.minecraft.world.phys.shapes.VoxelShape shape = call(PcbCircuitBlockEntity.class, board, "shape");
            check(shape.toAabbs().stream().anyMatch(box -> box.contains(mounted)), "форма кнопки на грани " + face);
        }
        for (int rotation = 0; rotation < 4; rotation++) for (int mirrored = 0; mirrored < 2; mirrored++) {
            int sliderDefinition = PcbWiring.component(15, rotation, 5, 0, false);
            if (mirrored != 0) sliderDefinition = call(PcbWiring.TYPE, null, "mirror", sliderDefinition);
            for (var face : net.minecraft.core.Direction.values()) {
                for (int percent : new int[]{0, 25, 50, 75, 100}) {
                    double x = 0;
                    double z = potentiometerHeight() * (percent / 100.0 - .5);
                    for (int turn = 0; turn < rotation; turn++) {
                        double nextX = -z; z = x; x = nextX;
                    }
                    var local = new net.minecraft.world.phys.Vec3((5 + PcbFootprint.width(sliderDefinition) / 2.0 + x) / 16,
                            2.0 / 16, (5 + PcbFootprint.height(sliderDefinition) / 2.0 + z) / 16);
                    net.minecraft.world.phys.Vec3 mounted = call(PcbCircuitBlock.class, null, "mountedPoint", local, face);
                    net.minecraft.world.phys.Vec3 restored = call(PcbCircuitBlock.class, null, "localPoint", mounted, face);
                    check((int) call(PcbCircuitBlock.class, null, "sliderPosition", restored, 85, sliderDefinition) == percent,
                            "положение ползунка " + face + "/" + rotation + "/" + percent);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("Платы: " + (PcbWiring.KINDS.length * 8) + " вариантов корпуса и поворота, настройка/перенос/удаление через меню, компиляция, намеренный обрыв, слои, старый формат, шесть граней, 240 положений ползунка и 11 выводов на корпусе регистра3×2 — успешно"), false);
        return checks;
    }
}
