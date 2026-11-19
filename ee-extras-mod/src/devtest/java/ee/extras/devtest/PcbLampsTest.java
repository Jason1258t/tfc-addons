package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import ee.extras.pcb.*;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Тот же последовательный макет LED/лампы/панели, что в исторической клиентской галерее. */
final class PcbLampsTest {
    private static final BlockPos BOARD = new BlockPos(82, -60, 16), TABLE = BOARD.west(2), BATTERY = BOARD.offset(2, 0, 5);

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcblamps").requires(s -> s.hasPermission(2))
                .then(Commands.literal("build").executes(c -> build(c.getSource())))
                .then(Commands.literal("expect").then(Commands.argument("mask", IntegerArgumentType.integer(0, 7))
                        .executes(c -> report(c.getSource(), IntegerArgumentType.getInteger(c, "mask"))))));
    }

    private static int build(CommandSourceStack source) {
        var level = source.getLevel();
        DevTest.place(level, TABLE, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        var editor = (PcbEditorBlockEntity) level.getBlockEntity(TABLE);
        editor.clear(2);
        for (int[] module : new int[][]{{13, 3}, {4, 7}, {18, 11}})
            require(editor.setPart(0, 7 * 16 + module[1], PcbModulesTest.definition(module[0], 0)), "установка модуля");
        for (int x = 0; x < 16; x++) {
            if (x == 3 || x == 7 || x == 8 || x == 11 || x == 12) continue;
            editor.setCell(0, 7 * 16 + x, x == 0 || x == 15 ? PcbTopology.TERMINAL : PcbTopology.TRACE);
        }
        var topology = editor.topology().copy();
        var parts = compile(topology);
        require(parts.size() == 3 && parts.get(0).kind() == PcbCircuitDevice.Kind.LED
                && parts.get(1).kind() == PcbCircuitDevice.Kind.LAMP
                && parts.get(2).kind() == PcbCircuitDevice.Kind.LED_PANEL, "три настоящие детали");
        var stack = PcbItems.L2_BOARD.toStack();
        PcbTopology.attach(stack, topology);
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM, true));
        PcbModulesTest.call(PcbCircuitBlockEntity.class, level.getBlockEntity(BOARD), "setBoard", stack);
        PcbModulesTest.call(PcbCircuitDevice.class, DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class),
                "setCircuit", parts, PcbCircuitDevice.Rating.L2, 2);
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.wire(level, BATTERY, 1, BOARD, 0);
        DevTest.wire(level, BATTERY, 0, BOARD, 1);
        source.getServer().getCommands().performPrefixedCommand(source, "pcbprotection power 24 84 -60 21");
        return 1;
    }

    private static List<PcbCircuitDevice.Part> compile(CompoundTag topology) {
        try { return PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbWiring"), null, "compile", topology); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    private static int report(CommandSourceStack source, int mask) {
        var level = source.getLevel();
        var board = (PcbCircuitBlockEntity) level.getBlockEntity(BOARD);
        long actual = PcbModulesTest.call(PcbCircuitBlockEntity.class, board, "indicatorSections", 0, 3);
        require(actual == mask, "свет " + actual + " вместо " + mask);
        var device = DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class);
        var tag = new CompoundTag();
        device.write(tag);
        require(!tag.getBoolean("Burnt"), "штатный макет сгорел");
        var serialized = tag.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var results = PcbBridgeTest.RESULTS.get(level);
        require(results != null, "нет результатов EE");
        double sum = 0;
        for (int i = 0; i < serialized.size(); i++) {
            var part = serialized.getCompound(i);
            double volts = Math.sqrt(results.getVoltageAtSqr(BOARD, part.getInt("A"), part.getInt("B")));
            require(Double.isFinite(volts) && (mask == 0 ? volts < .01 : volts > 3), "напряжение " + part.getString("Kind") + ": " + volts);
            sum += volts;
            double measured = volts;
            source.sendSuccess(() -> Component.literal("PCB_LAMPS: " + part.getString("Kind") + " Volts=" + measured), false);
        }
        require(Math.abs(sum - (mask == 0 ? 0 : 24)) < .02, "сумма напряжений " + sum);
        require(Double.isFinite(tag.getDouble("Watts")) && (mask == 0 ? tag.getDouble("Watts") < .001 : tag.getDouble("Watts") > .01), "реальная мощность " + tag);
        source.sendSuccess(() -> Component.literal("PCB_LAMPS_OK: Mask=" + actual + ", Watts=" + tag.getDouble("Watts")), false);
        return 1;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("PCB_LAMPS_FAIL: " + message);
    }
}
