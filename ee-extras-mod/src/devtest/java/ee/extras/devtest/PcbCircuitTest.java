package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbCircuitDevice;
import ee.extras.pcb.PcbCircuitDevice.Kind;
import ee.extras.pcb.PcbCircuitDevice.Part;
import ee.extras.pcb.PcbItems;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

final class PcbCircuitTest {
    private static final BlockPos BOARD = new BlockPos(8, -60, 8);
    private static final BlockPos BATTERY = BOARD.offset(0, 0, 4);

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcbcircuit").requires(source -> source.hasPermission(2))
                .then(Commands.literal("build").executes(context -> build(context.getSource().getLevel())))
                .then(Commands.literal("press").executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class)
                            .interact(false, level.getGameTime());
                    return 1;
                }))
                .then(Commands.literal("logic").executes(context -> logic(context.getSource().getLevel())))
                .then(Commands.literal("toggle").executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class)
                            .interact(true, level.getGameTime());
                    return 1;
                }))
                .then(Commands.literal("expect_output").then(Commands.argument("powered", BoolArgumentType.bool())
                        .executes(context -> expectOutput(context.getSource(), BoolArgumentType.getBool(context, "powered")))))
                .then(Commands.literal("expect").then(Commands.argument("lit", BoolArgumentType.bool())
                        .executes(context -> expect(context.getSource(), BoolArgumentType.getBool(context, "lit"))))));
    }

    private static int build(ServerLevel level) {
        DevTest.place(level, BOARD, Blocks.AIR.defaultBlockState());
        DevTest.place(level, BATTERY, Blocks.AIR.defaultBlockState());
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState());
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.wire(level, BATTERY, 0, BOARD, 0);
        DevTest.wire(level, BATTERY, 1, BOARD, 1);
        DevicesSavedData data = DevicesSavedData.load(level);
        data.getDevice(BATTERY, CreativeBatteryDevice.class).voltage = 300;
        // Кнопка питает катушку, реле включает делитель. На лампе ожидается 150 В.
        data.getDevice(BOARD, PcbCircuitDevice.class).setCircuit(List.of(
                part(Kind.BUTTON, 0, 4, 1, 1),
                new Part(Kind.RELAY, 0, 5, 1000, 4, 1, 60),
                part(Kind.RESISTOR, 5, 6, 1000, 1),
                part(Kind.LAMP, 6, 1, 1000, 140)));
        return 1;
    }

    private static int logic(ServerLevel level) {
        DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class).setCircuit(List.of(
                part(Kind.TRACE, 0, 4, 1, 1),
                part(Kind.REDSTONE_IN, 4, 5, 1, 1),
                part(Kind.SWITCH, 5, 6, 1, 1),
                part(Kind.REDSTONE_OUT, 6, 1, 1000, 140)));
        return 1;
    }

    private static int expectOutput(CommandSourceStack source, boolean expected) {
        int actual = source.getLevel().getSignal(BOARD, Direction.EAST);
        if ((actual == 15) != expected) {
            source.sendFailure(Component.literal("Плата: ожидался выход " + expected + ", сигнал " + actual));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Плата: сигнал западного выхода " + actual), false);
        return 1;
    }

    private static Part part(Kind kind, int a, int b, double ohms, double threshold) {
        return new Part(kind, a, b, ohms, 0, 0, threshold);
    }

    private static int expect(CommandSourceStack source, boolean expected) {
        boolean actual = source.getLevel().getBlockState(BOARD).getValue(PcbCircuitBlock.LIT);
        CompoundTag diagnostics = new CompoundTag();
        DevicesSavedData.load(source.getLevel()).getDevice(BOARD, PcbCircuitDevice.class).write(diagnostics);
        double volts = diagnostics.getDouble("IndicatorVoltage");
        double target = expected ? 150 : 0;
        if (!Double.isFinite(volts) || Math.abs(volts - target) > 1) {
            source.sendFailure(Component.literal("Плата: ожидалось " + target + " В, измерено " + volts + " В"));
            return 0;
        }
        if (expected != actual) {
            source.sendFailure(Component.literal("Плата: ожидалось свечение " + expected + ", получено " + actual));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Плата: свечение " + actual + " подтверждено, " + volts + " В"), false);
        return 1;
    }
}
