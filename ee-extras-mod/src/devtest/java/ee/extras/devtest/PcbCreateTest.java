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
        root.then(Commands.literal("enabled").then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                .executes(context -> enabled(context.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value")))));
        root.then(Commands.literal("saved").executes(context -> saved(context.getSource())));
        root.then(Commands.literal("unloaded").executes(context -> {
            var level = context.getSource().getLevel();
            PcbExtraTest.check(!level.hasChunk(BOARD.getX() >> 4, BOARD.getZ() >> 4), "чанк платы действительно выгружен");
            context.getSource().sendSuccess(() -> Component.literal("Чанк адаптера выгружен"), false);
            return 1;
        }));
        root.then(Commands.literal("load").then(Commands.argument("ohms", DoubleArgumentType.doubleArg(.01, 1e9))
                .executes(context -> load(context.getSource(), DoubleArgumentType.getDouble(context, "ohms")))));
        root.then(Commands.literal("ac").executes(context -> {
            var level = context.getSource().getLevel();
            power(level, SUPPLY, 5 * Math.sqrt(2));
            level.setBlock(SUPPLY, level.getBlockState(SUPPLY).setValue(
                    com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock.AC, true), 3);
            var battery = DevicesSavedData.load(level).getDevice(SUPPLY, CreativeBatteryDevice.class);
            battery.acFrequency = 50;
            return 1;
        }));
        root.then(Commands.literal("balance").executes(context -> balance(context.getSource())));
        root.then(Commands.literal("check-load").executes(context -> checkLoad(context.getSource())));
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
        routing.part(211, 0, new int[]{2, 1}, 1e9, 0);
        for (int pin = 0; pin < 4; pin++) routing.terminal(PcbDemoRouting.terminalCell(pin), pin);
        var topology = routing.build();
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM, true));
        var board = PcbItems.L2_BOARD.toStack();
        PcbTopology.attach(board, topology);
        PcbModulesTest.call(PcbCircuitBlockEntity.class, level.getBlockEntity(BOARD), "setBoard", board);
        var compiled = PcbModulesTest.<java.util.List<PcbCircuitDevice.Part>>call(PcbBridgeTest.wiring(), null, "compile", topology);
        PcbExtraTest.check(compiled != null && compiled.size() == 5, "компиляция ручки и адаптера Create");
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
        // Механическое табло питается настоящим зацеплением с отдельной шестернёй, не подменой скорости.
        for (int x = 0; x < 3; x++) DevTest.place(level, ANALOG_TARGET.east(x), Blocks.AIR.defaultBlockState());
        DevTest.place(level, ANALOG_TARGET, AllBlocks.DISPLAY_BOARD.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        DevTest.place(level, ANALOG_TARGET.east(), AllBlocks.COGWHEEL.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Z));
        DevTest.place(level, ANALOG_TARGET.east().south(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH));
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
                level.getBlockEntity(ANALOG_TARGET.east().south())).generatedSpeed.setValue(128);
        link(level, BOARD.south(), Direction.SOUTH, STATE_TARGET, true);
        link(level, BOARD.above(), Direction.UP, SECOND_TARGET, false);
        ((DisplayLinkBlockEntity) level.getBlockEntity(BOARD.above())).getSourceConfig().putInt("Adapter", 1);
        DevTest.place(level, BOARD.west(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createdeco:green_brass_lamp"))
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.WEST));
        source.sendSuccess(() -> Component.literal("Адаптер Create: две ручки, два адаптера, три Display Link, механическое табло, два Nixie и лампа"), false);
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
        level.setBlock(position, level.getBlockState(position).setValue(
                com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock.AC, false), 3);
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
        var entity = level.getBlockEntity(position);
        if (entity instanceof com.simibubi.create.content.trains.display.FlapDisplayBlockEntity flap) {
            var controller = flap.getController();
            PcbExtraTest.check(controller != null && controller.isSpeedRequirementFulfilled(),
                    "реальный привод механического табло работает");
            var result = new StringBuilder();
            for (var section : controller.getLines().getFirst().getSections()) {
                String json = section.write(level.registryAccess()).getString("Text");
                var component = json.isEmpty() ? Component.empty() : Component.Serializer.fromJson(json, level.registryAccess());
                PcbExtraTest.check(component != null, "текст секции механического табло");
                result.append(component.getString());
            }
            return Component.Serializer.toJson(Component.literal(result.toString()), level.registryAccess());
        }
        return entity.saveWithFullMetadata(level.registryAccess()).getString("CustomText");
    }

    private static String plainText(ServerLevel level, BlockPos position) {
        var component = Component.Serializer.fromJson(text(level, position), level.registryAccess());
        PcbExtraTest.check(component != null, "текст физического приёмника " + position);
        return component.getString();
    }

    static int enabled(CommandSourceStack source, boolean expected) {
        var tag = new CompoundTag();
        device(source.getLevel()).write(tag);
        PcbExtraTest.check(!tag.getBoolean("Burnt"), "плата не сгорела");
        for (String index : new String[]{"2", "3"}) {
            var adapter = tag.getCompound("CreateInterfaces").getCompound(index);
            PcbExtraTest.check(adapter.contains("Powered") && adapter.getBoolean("Powered") == expected,
                    "питание адаптера " + index + ": " + adapter);
        }
        source.sendSuccess(() -> Component.literal("Оба адаптера включены: " + expected), false);
        return 1;
    }

    static int load(CommandSourceStack source, double ohms) {
        var tag = new CompoundTag();
        var device = device(source.getLevel());
        device.write(tag);
        var parts = tag.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND);
        PcbExtraTest.check(parts.size() == 5 && parts.getCompound(4).getString("Kind").equals("RESISTOR"),
                "видимый нагрузочный резистор");
        parts.getCompound(4).putDouble("Ohms", ohms);
        device.read(tag);
        return 1;
    }

    static int balance(CommandSourceStack source) {
        var tag = new CompoundTag();
        device(source.getLevel()).write(tag);
        var first = tag.getCompound("CreateInterfaces").getCompound("2");
        var second = tag.getCompound("CreateInterfaces").getCompound("3");
        double supply = first.getDouble("Supply"), analog = first.getDouble("Analog");
        double other = second.getDouble("Analog");
        double load = tag.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(4).getDouble("Ohms");
        double expected = 2 * supply * supply / 10000;
        expected += (supply - analog) * (supply - analog) / 5000.01 + analog * analog / 5000;
        expected += (supply - other) * (supply - other) / 2500.01 + other * other / 7500;
        expected += analog * analog / (load + .01);
        for (var adapter : new CompoundTag[]{first, second}) {
            double a = adapter.getDouble("Analog"), digital = adapter.getDouble("Digital");
            expected += (a * a + digital * digital) / 1e9;
        }
        double residual = tag.getDouble("Watts") - expected;
        PcbExtraTest.check(Double.isFinite(residual) && Math.abs(residual) < 1e-5,
                "независимый баланс мощности всей платы: " + residual + " Вт");
        double finalExpected = expected;
        source.sendSuccess(() -> Component.literal("Баланс Create: измерено=" + tag.getDouble("Watts")
                + " расчёт=" + finalExpected + " остаток=" + residual + " Вт"), false);
        return 1;
    }

    static int checkLoad(CommandSourceStack source) {
        var tag = new CompoundTag();
        device(source.getLevel()).write(tag);
        var adapter = tag.getCompound("CreateInterfaces").getCompound("2");
        double ohms = tag.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(4).getDouble("Ohms");
        // Делитель 5к + (5к || 1ГОм входа || внешняя нагрузка), без обращения к реализации адаптера.
        double bottom = 1 / (1.0 / 5000 + 1.0 / 1e9 + 1 / (ohms + .01));
        double expected = adapter.getDouble("Supply") * bottom / (5000.01 + bottom);
        PcbExtraTest.check(adapter.getBoolean("Powered") && !tag.getBoolean("Burnt"), "нагруженный адаптер питается");
        check(source, expected);
        balance(source);
        source.sendSuccess(() -> Component.literal("Нагрузка " + ohms + " Ом: делитель подтверждён"), false);
        return 1;
    }

    static int saved(CommandSourceStack source) {
        var level = source.getLevel();
        PcbExtraTest.check(level.getBlockEntity(BOARD) instanceof PcbCircuitBlockEntity, "сохранённая плата существует");
        var tag = new CompoundTag();
        device(level).write(tag);
        PcbExtraTest.check(tag.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 5,
                "сохранены пять смонтированных модулей");
        int[] positions = tag.getIntArray("Positions");
        PcbExtraTest.check(positions.length >= 2 && positions[0] == 50 && positions[1] == 25,
                "сохранены положения обеих ручек");
        var link = (DisplayLinkBlockEntity) level.getBlockEntity(BOARD.above());
        PcbExtraTest.check(link != null && link.getSourceConfig().getInt("Adapter") == 1 && link.activeSource != null,
                "сохранён выбор второго адаптера и источник Create");
        enabled(source, true);
        check(source, 2.5);
        displays(source);
        state(source, true);
        source.sendSuccess(() -> Component.literal("Сохранённая схема Create восстановлена"), false);
        return 1;
    }

    static int displays(CommandSourceStack source) {
        var level = source.getLevel();
        String analog = plainText(level, ANALOG_TARGET), digital = plainText(level, STATE_TARGET);
        PcbExtraTest.check(analog.equals("2.50") && digital.equals("1"), "табло и Nixie получили данные: " + analog + "/" + digital);
        String second = plainText(level, SECOND_TARGET);
        PcbExtraTest.check(second.equals("3.75"), "второй адаптер независимо выбран: " + second);
        PcbExtraTest.check(PcbModulesTest.call(PcbCircuitDevice.class, device(level), "createInterface", -1) == null
                && PcbModulesTest.call(PcbCircuitDevice.class, device(level), "createInterface", 2) == null,
                "неверный номер не подменяется первым адаптером");
        source.sendSuccess(() -> Component.literal("Три физических приёмника Create: " + analog + " / " + digital), false);
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
            PcbExtraTest.check(component != null && component.getString().isEmpty(), "очистка приёмника " + position);
        }
        PcbExtraTest.check(level.getBlockState(BOARD).getValue(PcbCircuitBlock.OUTPUT) == 0, "выход лампы выключен");
        source.sendSuccess(() -> Component.literal("Оба табло и выход лампы очищены при отключении"), false);
        return 1;
    }
}
