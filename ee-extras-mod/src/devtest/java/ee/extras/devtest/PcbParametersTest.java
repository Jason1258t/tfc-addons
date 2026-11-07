package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.pcb.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Произвольный номинал проходит меню, перенос, чертёж и настоящий расчёт EE. */
final class PcbParametersTest {
    private static final BlockPos TABLE = new BlockPos(2, -60, 28), BOARD = TABLE.east(2), SUPPLY = BOARD.east(4);
    private static final double RESISTANCE = 733.25;
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcbparameters").requires(s -> s.hasPermission(2))
                .then(Commands.literal("build").executes(c -> build(c.getSource())))
                .then(Commands.literal("expect").executes(c -> expect(c.getSource()))));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException("Параметры платы: " + message); }
    private static boolean apply(PcbEditorMenu menu, double value, double threshold) {
        int anchor = PcbModulesTest.call(PcbEditorMenu.class, menu, "selected");
        int definition = PcbModulesTest.call(PcbEditorMenu.class, menu, "selectedDefinition");
        return PcbModulesTest.call(PcbEditorMenu.class, menu, "applyParameters", new PcbEditorPackets.Parameters(menu.containerId, anchor, definition, value, threshold, PcbModulesTest.call(PcbEditorMenu.class, menu, "instanceId")));
    }
    private static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        DevTest.place(level, TABLE, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        var editor = (PcbEditorBlockEntity) level.getBlockEntity(TABLE);
        editor.clear(1);
        var menu = new PcbEditorMenu(84, player.getInventory(), ContainerLevelAccess.create(level, TABLE), editor);
        menu.clickMenuButton(player, 1040);
        check(apply(menu, RESISTANCE, 3), "номинал до установки");
        menu.clickMenuButton(player, 68);
        check(editor.topology().getCompound("ModuleParameters").getCompound("68").getDouble("Nominal") == RESISTANCE, "установка сохраняет номинал");
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 1e10}) check(!apply(menu, invalid, 3), "отклонён номинал " + invalid);
        int definition = PcbModulesTest.call(PcbEditorMenu.class, menu, "selectedDefinition");
        long instance = PcbModulesTest.call(PcbEditorMenu.class, menu, "instanceId");
        check(instance != 0, "экземпляр имеет идентификатор");
        check(!(boolean) PcbModulesTest.call(PcbEditorMenu.class, menu, "applyParameters", new PcbEditorPackets.Parameters(84, 68, definition, 100, 3, instance ^ 1)), "чужой экземпляр отклонён");
        check(!(boolean) PcbModulesTest.call(PcbEditorMenu.class, menu, "applyParameters", new PcbEditorPackets.Parameters(999, 68, definition, 100, 3, instance)), "чужое меню отклонено");
        check(!(boolean) PcbModulesTest.call(PcbEditorMenu.class, menu, "applyParameters", new PcbEditorPackets.Parameters(84, 69, definition, 100, 3, instance)), "устаревший выбор отклонён");
        menu.clickMenuButton(player, PcbEditorMenu.ROTATE);
        check(editor.topology().getCompound("ModuleParameters").getCompound("68").getDouble("Nominal") == RESISTANCE, "поворот сохраняет номинал");
        menu.clickMenuButton(player, PcbEditorMenu.ROTATE);
        menu.clickMenuButton(player, PcbEditorMenu.ROTATE);
        menu.clickMenuButton(player, PcbEditorMenu.ROTATE);
        menu.clickMenuButton(player, PcbEditorMenu.SELECT_TOOL + 7);
        menu.clickMenuButton(player, 68); menu.clickMenuButton(player, 100);
        check(!editor.topology().getCompound("ModuleParameters").contains("68")
                && editor.topology().getCompound("ModuleParameters").getCompound("100").getDouble("Nominal") == RESISTANCE, "перенос атомарно переносит параметры");
        check(editor.topology().getCompound("ModuleIds").getLong("100") == instance && !editor.topology().getCompound("ModuleIds").contains("68"), "перенос сохраняет идентичность");
        for (int x = 0; x < 16; x++) if (x != 4) editor.setCell(0, 96 + x, x == 0 || x == 15 ? PcbTopology.TERMINAL : PcbTopology.TRACE);
        check(PcbTopology.validate(editor.topology()) == null, "схема с номиналом валидна");
        var copy = PcbTopology.create(1); PcbTopology.copyInto(PcbTopology.normalize(editor.topology(), 1), copy);
        check(copy.getCompound("ModuleParameters").getCompound("100").getDouble("Nominal") == RESISTANCE, "нормализация и копирование");
        check(diagnostics(copy).isEmpty(), "проверка принимает исправную схему");
        var disconnected = copy.copy(); PcbTopology.set(disconnected, 0, 99, PcbTopology.EMPTY);
        var issues = diagnostics(disconnected);
        check(issues.stream().anyMatch(issue -> (int) PcbModulesTest.call(issue.getClass(), issue, "cell") == 99
                && (int) PcbModulesTest.call(issue.getClass(), issue, "layer") == 0), "проверка указывает точное место обрыва");
        var erased = copy.copy(); PcbTopology.set(erased, 0, 100, PcbTopology.EMPTY);
        check(!erased.getCompound("ModuleIds").contains("100"), "удаление освобождает идентификатор");
        check(!erased.getCompound("ModuleParameters").contains("100"), "удаление не оставляет параметров");
        var broken = copy.copy(); broken.getCompound("ModuleParameters").getCompound("100").putDouble("Nominal", Double.NaN);
        check(PcbTopology.validate(broken) != null, "поддельный чертёж отклонён");
        // Проверка единиц и шага таймеров через тот же метод меню.
        menu.clickMenuButton(player, 1040 + 16);
        check(apply(menu, 3.75, 3) && !apply(menu, 3.76, 3), "таймер: секунды с шагом тика");
        menu.clickMenuButton(player, 1040 + 9);
        check(apply(menu, 10, .075) && !apply(menu, 10, 101), "предохранитель: порог в амперах");
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM, true));
        var stack = PcbItems.L1_BOARD.toStack(); PcbTopology.attach(stack, copy);
        ((PcbCircuitBlock) level.getBlockState(BOARD).getBlock()).setPlacedBy(level, BOARD, level.getBlockState(BOARD), player, stack);
        var saved = new CompoundTag(); device(level).write(saved);
        check(saved.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getDouble("Ohms") == RESISTANCE, "компиляция в EE без округления к ряду");
        DevTest.place(level, SUPPLY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        var battery = DevicesSavedData.load(level).getDevice(SUPPLY, CreativeBatteryDevice.class);
        var entity = (com.simibubi.create.foundation.blockEntity.SmartBlockEntity) level.getBlockEntity(SUPPLY);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(24000);
        battery.voltage = 24; battery.acFrequency = 0;
        DevTest.wire(level, SUPPLY, 0, BOARD, 0); DevTest.wire(level, SUPPLY, 1, BOARD, 1);
        source.sendSuccess(() -> Component.literal("Параметры: дробный номинал, перенос, поворот, нормализация, удаление, неверные числа, поддельный чертёж и единицы — успешно"), false);
        return 1;
    }
    private static java.util.List<?> diagnostics(CompoundTag topology) {
        try { return PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbDiagnostics"), null, "check", topology); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }
    private static PcbCircuitDevice device(ServerLevel level) { return DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class); }
    private static int expect(CommandSourceStack source) {
        var saved = new CompoundTag(); device(source.getLevel()).write(saved);
        double expected = 24 * 24 / RESISTANCE, actual = saved.getDouble("Watts");
        check(!saved.getBoolean("Burnt") && Math.abs(actual - expected) < .001, "мощность " + actual + " вместо " + expected);
        var restored = saved.copy(); device(source.getLevel()).read(restored);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Резистор733,25Ом: %.6fВт, ожидалось%.6fВт; write/read пройден", actual, expected)), false);
        return 1;
    }
}
