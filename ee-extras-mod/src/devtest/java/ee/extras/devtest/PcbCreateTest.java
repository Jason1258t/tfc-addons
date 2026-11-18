package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import ee.extras.pcb.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Настоящая плата, внешние источники и два независимых Display Link с приёмниками Create. */
final class PcbCreateTest {
    static BlockPos BOARD = new BlockPos(240, -60, 240);
    static BlockPos SUPPLY = BOARD.east(5);
    static BlockPos DIGITAL = BOARD.east(8);
    static BlockPos ANALOG_TARGET = BOARD.north(5);
    static BlockPos STATE_TARGET = BOARD.south(5);
    static BlockPos SECOND_TARGET = BOARD.north(8);

    static void origin(BlockPos origin) {
        BOARD = origin; SUPPLY = BOARD.east(5); DIGITAL = BOARD.east(8);
        ANALOG_TARGET = BOARD.north(5); STATE_TARGET = BOARD.south(5); SECOND_TARGET = BOARD.north(8);
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("pcbcreate").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("build").executes(context -> build(context.getSource())));
        root.then(Commands.literal("supply").then(Commands.argument("volts", DoubleArgumentType.doubleArg(-7, 7))
                .executes(context -> { power(context.getSource().getLevel(), SUPPLY,
                        DoubleArgumentType.getDouble(context, "volts")); return 1; })));
        root.then(Commands.literal("digital").then(Commands.argument("volts", DoubleArgumentType.doubleArg(-7, 7))
                .executes(context -> { power(context.getSource().getLevel(), DIGITAL,
                        DoubleArgumentType.getDouble(context, "volts")); return 1; })));
        root.then(Commands.literal("check").then(Commands.argument("volts", DoubleArgumentType.doubleArg(-7, 7))
                .executes(context -> check(context.getSource(), DoubleArgumentType.getDouble(context, "volts")))));
        root.then(Commands.literal("displays").executes(context -> displays(context.getSource())));
        root.then(Commands.literal("state").then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                .executes(context -> state(context.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value")))));
        root.then(Commands.literal("blank").executes(context -> blank(context.getSource())));
        dispatcher.register(root);
    }

    static int build(CommandSourceStack source) {
        var level = source.getLevel();
        DevTest.place(level, BOARD, Blocks.AIR.defaultBlockState());
        var routing = new PcbDemoRouting(5);
        routing.part(51, 36, new int[]{0, 1, 2}, 10000, 0);
        routing.part(57, 36, new int[]{0, 1, 4}, 10000, 0);
        routing.part(117, 40, new int[]{2, 3, 0, 1}, 0, 0);
        routing.part(123, 40, new int[]{4, 1, 0, 1}, 0, 0);
        for (int pin = 0; pin < 4; pin++) routing.terminal(PcbDemoRouting.terminalCell(pin), pin);
        var topology = routing.build();
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM, true));
        var board = PcbItems.L2_BOARD.toStack();
        PcbTopology.attach(board, topology);
        PcbModulesTest.call(PcbCircuitBlockEntity.class, level.getBlockEntity(BOARD), "setBoard", board);
        var compiled = PcbModulesTest.<java.util.List<PcbCircuitDevice.Part>>call(PcbBridgeTest.wiring(), null, "compile", topology);
        PcbExtraTest.check(compiled != null && compiled.size() == 4, "компиляция ручки и адаптера Create");
        PcbModulesTest.call(PcbCircuitDevice.class, device(level), "setCircuit", compiled, PcbCircuitDevice.Rating.L2, 4);
        PcbModulesTest.call(PcbCircuitDevice.class, device(level), "adjustComponent", 0, 50);
        PcbModulesTest.call(PcbCircuitDevice.class, device(level), "adjustComponent", 1, 25);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        for (BlockPos battery : new BlockPos[]{SUPPLY, DIGITAL}) {
            DevTest.place(level, battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            PcbExtraTest.check(DevTest.spoolWire(player, ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),
                    battery, 0, BOARD, 1), "земля внешнего источника");
            PcbExtraTest.check(DevTest.spoolWire(player, ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),
                    battery, 1, BOARD, battery.equals(SUPPLY) ? 0 : 3), "положительный провод");
            power(level, battery, 5);
        }
        link(level, BOARD.north(), Direction.NORTH, ANALOG_TARGET, false);
        link(level, BOARD.south(), Direction.SOUTH, STATE_TARGET, true);
        link(level, BOARD.above(), Direction.UP, SECOND_TARGET, false);
        ((DisplayLinkBlockEntity) level.getBlockEntity(BOARD.above())).getSourceConfig().putInt("Adapter", 1);
        DevTest.place(level, BOARD.west(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createdeco:green_brass_lamp"))
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.WEST));
        source.sendSuccess(() -> Component.literal("Адаптер Create: ручка, питание, два физических Display Link и два табло Nixie"), false);
        return 1;
    }

    private static void link(ServerLevel level, BlockPos position, Direction facing, BlockPos target, boolean digital) {
        for (int x = 0; x < 3; x++) {
            DevTest.place(level, target.east(x).below(), Blocks.STONE.defaultBlockState());
            DevTest.place(level, target.east(x), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:nixie_tube")).defaultBlockState());
        }
        DevTest.place(level, position, AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, facing));
        var link = (DisplayLinkBlockEntity) level.getBlockEntity(position);
        var registry = level.registryAccess().registryOrThrow(CreateRegistries.DISPLAY_SOURCE);
        link.activeSource = registry.get(ResourceLocation.fromNamespaceAndPath("ee_extras",
                digital ? "pcb_create_state" : "pcb_create_voltage"));
        PcbExtraTest.check(link.activeSource != null && DisplaySource.getAll(level, BOARD).contains(link.activeSource),
                "источник зарегистрирован на плате");
        link.target(target);
        link.setChanged();
    }

    static PcbCircuitDevice device(ServerLevel level) {
        return DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class);
    }

    static void power(ServerLevel level, BlockPos position, double volts) {
        var battery = DevicesSavedData.load(level).getDevice(position, CreativeBatteryDevice.class);
        battery.voltage = volts;
        battery.acFrequency = 0;
        var entity = (com.simibubi.create.foundation.blockEntity.SmartBlockEntity) level.getBlockEntity(position);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE)
                .setValue((int) Math.round(volts * 1000));
    }

    static int check(CommandSourceStack source, double expected) {
        var data = new CompoundTag();
        device(source.getLevel()).write(data);
        var adapter = data.getCompound("CreateInterfaces").getCompound("2");
        double actual = adapter.getDouble("Analog");
        PcbExtraTest.check(!data.getBoolean("Burnt") && Math.abs(actual - expected) < .001,
                "аналоговый вход адаптера " + actual + " вместо " + expected);
        double supply = adapter.getDouble("Supply"), digital = adapter.getDouble("Digital");
        double expectedWatts = supply * supply / 10000 + actual * actual / 1e9 + digital * digital / 1e9;
        PcbExtraTest.check(Math.abs(adapter.getDouble("Watts") - expectedWatts) < 1e-8,
                "независимая мощность питания и обоих входов");
        source.sendSuccess(() -> Component.literal("Create: питание=" + adapter.getDouble("Supply")
                + " А=" + actual + " Л=" + adapter.getDouble("Digital")
                + " работает=" + adapter.getBoolean("Powered") + " выход=" + adapter.getBoolean("High")
                + " мощность=" + adapter.getDouble("Watts")), false);
        return 1;
    }

    private static String text(ServerLevel level, BlockPos position) {
        return level.getBlockEntity(position).saveWithFullMetadata(level.registryAccess()).getString("CustomText");
    }

    static int displays(CommandSourceStack source) {
        var level = source.getLevel();
        String analog = text(level, ANALOG_TARGET), digital = text(level, STATE_TARGET);
        PcbExtraTest.check(analog.contains("2.50") && digital.contains("1"), "обе Nixie получили данные: " + analog + "/" + digital);
        String second = text(level, SECOND_TARGET);
        PcbExtraTest.check(second.contains("3.75"), "второй адаптер независимо выбран: " + second);
        PcbExtraTest.check(PcbModulesTest.call(PcbCircuitDevice.class, device(level), "createInterface", -1) == null
                && PcbModulesTest.call(PcbCircuitDevice.class, device(level), "createInterface", 2) == null,
                "неверный номер не подменяется первым адаптером");
        source.sendSuccess(() -> Component.literal("Два физических приёмника Create: " + analog + " / " + digital), false);
        return 1;
    }

    static int state(CommandSourceStack source, boolean expected) {
        var level = source.getLevel();
        var lamp = level.getBlockState(BOARD.west());
        PcbExtraTest.check(lamp.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) == expected,
                "физическая лампа Create Deco: " + lamp);
        PcbExtraTest.check(level.getBlockState(BOARD).getValue(PcbCircuitBlock.OUTPUT) == (expected ? 15 : 0),
                "ориентированный выход адаптера");
        source.sendSuccess(() -> Component.literal("Лампа Create Deco: " + expected), false);
        return 1;
    }

    static int blank(CommandSourceStack source) {
        var level = source.getLevel();
        for (var position : new BlockPos[]{ANALOG_TARGET, STATE_TARGET, SECOND_TARGET}) {
            var component = Component.Serializer.fromJson(text(level, position), level.registryAccess());
            PcbExtraTest.check(component != null && component.getString().isEmpty(), "очистка Nixie " + position);
        }
        PcbExtraTest.check(level.getBlockState(BOARD).getValue(PcbCircuitBlock.OUTPUT) == 0, "выход лампы выключен");
        source.sendSuccess(() -> Component.literal("Оба табло и выход лампы очищены при отключении"), false);
        return 1;
    }
}
