package tfc.destroy.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.AllBlocks;
import java.util.List;
import java.util.Map;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import blusunrize.immersiveengineering.api.IEProperties;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ElectrolyzerLogic;
import tfc.destroy.extras.electrolyzer.ElectrolyzerStatus;

/** Мультиблок 0.8.0: сборка молотом с четырёх сторон, клеммы EE, порты по ролям, разборка, рецепт другой реакции. */
final class ElectrolyzerMbTest {
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final BlockPos BROKEN = new BlockPos(60, -59, 30);
    private static final BlockPos WATER_CELL = new BlockPos(80, -59, 10);
    private static final Direction WATER_SIDE = Direction.NORTH;
    private static final ResourceLocation WATER_RECIPE = ResourceLocation.parse("destroy_extras_devtest:electrolysis/test_water");
    private static int checks;
    private static int failures;

    private ElectrolyzerMbTest() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("mbform").executes(ElectrolyzerMbTest::form))
            .then(Commands.literal("mbdisassemble").executes(ElectrolyzerMbTest::disassemble))
            .then(Commands.literal("mbwater").executes(ElectrolyzerMbTest::water))
            .then(Commands.literal("mbwaterassert").executes(ElectrolyzerMbTest::waterAssert))
            .then(Commands.literal("mbsave").executes(ElectrolyzerMbTest::save))
            .then(Commands.literal("mblava").executes(ElectrolyzerMbTest::lava))
            .then(Commands.literal("mblavaassert").executes(ElectrolyzerMbTest::lavaAssert)));
    }

    private static BlockPos cellFor(Direction side) {
        return new BlockPos(60 + SIDES.indexOf(side) * 8, -59, 10);
    }

    private static int form(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        for (Direction side : SIDES) {
            BlockPos cell = cellFor(side);
            ElectrolyzerRig.place(level, cell, side);
            BlockPos pump = ElectrolyzerRig.input(cell, side);
            check(ctx, DevicesSavedData.load(level).getDevice(pump) != null, side + ": до сборки у электропомпы своё устройство EE");
            check(ctx, ElectrolyzerRig.form(level, cell, side), side + ": молот собирает машину");
            BlockState formed = level.getBlockState(cell);
            check(ctx, formed.is(DestroyExtras.ELECTROLYZER.block().get()), side + ": ячейка стала частью машины");
            check(ctx, formed.getValue(IEProperties.FACING_HORIZONTAL) == side.getOpposite(), side + ": машина смотрит от игрока, как в IE");
            check(ctx, DevicesSavedData.load(level).getDevice(pump) instanceof ProcessPowerDevice,
                side + ": устройство электропомпы заменено частью машины");

            BlockPos power = ElectrolyzerRig.power(cell, side);
            ElectricalDeviceBlock<?> block = (ElectricalDeviceBlock<?>) level.getBlockState(power).getBlock();
            Map<Integer, Vec3> nodes = block.getNodePositions(level, power, level.getBlockState(power));
            check(ctx, nodes.size() == 2, side + ": на клеммной клетке два контакта EE");
            check(ctx, nodes.values().stream().allMatch(v -> v.y > 0.85 && v.x >= 0 && v.x <= 1 && v.z >= 0 && v.z <= 1),
                side + ": контакты наверху клетки и внутри неё: " + nodes.values());
            check(ctx, block.getNodePositions(level, cell, level.getBlockState(cell)).isEmpty(), side + ": у других клеток контактов нет");
            if (nodes.size() == 2) {
                Direction left = side.getOpposite().getCounterClockWise();
                Vec3 delta = nodes.get(ProcessPowerDevice.PLUS).subtract(nodes.get(ProcessPowerDevice.MINUS));
                check(ctx, delta.x * left.getStepX() + delta.z * left.getStepZ() > 0.3, side + ": «+» слева от игрока: " + nodes);
            }

            IFluidHandler input = level.getCapability(Capabilities.FluidHandler.BLOCK, pump, ElectrolyzerRig.worldSide(side, Direction.EAST));
            check(ctx, input != null && input.fill(new FluidStack(TFCFluids.SALT_WATER.getSource(), 100), IFluidHandler.FluidAction.SIMULATE) == 100,
                side + ": вход справа у электропомпы принимает жидкость");
            check(ctx, level.getCapability(Capabilities.FluidHandler.BLOCK, pump, ElectrolyzerRig.worldSide(side, Direction.SOUTH)) == null,
                side + ": у другой грани помпы порта нет");
            for (int[] port : new int[][] {{0, 2, 1}, {2, 2, 1}}) {
                BlockPos pos = ElectrolyzerRig.world(cell, side, port[0], port[1], port[2]);
                IFluidHandler gas = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
                check(ctx, gas != null && gas.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE) == 0,
                    side + ": газовый выход " + port[0] + " сверху есть и только отдаёт");
            }
        }
        // Неполная структура: без одного бака молот ничего не делает.
        ElectrolyzerRig.place(level, BROKEN, Direction.NORTH);
        BlockPos tank = ElectrolyzerRig.world(BROKEN, Direction.NORTH, 0, 1, 0);
        level.setBlock(tank, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check(ctx, !ElectrolyzerRig.form(level, BROKEN, Direction.NORTH), "без бака машина не собирается");
        check(ctx, level.getBlockState(BROKEN).is(DestroyExtras.ELECTROLYZER_BLOCK.get()), "ячейка неполной структуры осталась ячейкой");
        return report(ctx);
    }

    /** Сломать бак собранной машины (сторона север): IE возвращает все блоки шаблона. */
    private static int disassemble(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos cell = cellFor(Direction.NORTH);
        BlockPos power = ElectrolyzerRig.power(cell, Direction.NORTH);
        BlockPos battery = power.above(2);
        level.setBlock(battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState(), Block.UPDATE_ALL);
        wire(level, battery, 0, power, ProcessPowerDevice.PLUS);
        check(ctx, !InfrastructureSavedData.load(level).getNodesAt(power).isEmpty(), "к клеммам подключён провод");
        level.destroyBlock(ElectrolyzerRig.world(cell, Direction.NORTH, 0, 0, 0), false);
        check(ctx, level.getBlockState(cell).is(DestroyExtras.ELECTROLYZER_BLOCK.get()), "ячейка вернулась блоком");
        check(ctx, level.getBlockState(power).is(AllBlocks.COPPER_CASING.get()), "клеммная клетка вернулась медным корпусом");
        check(ctx, BuiltInRegistries.BLOCK.getKey(level.getBlockState(ElectrolyzerRig.input(cell, Direction.NORTH)).getBlock())
            .toString().equals("electroenergetics:electric_pump"), "электропомпа вернулась");
        check(ctx, level.getBlockState(ElectrolyzerRig.world(cell, Direction.NORTH, 0, 2, 1)).is(AllBlocks.FLUID_PIPE.get()), "труба вернулась");
        check(ctx, InfrastructureSavedData.load(level).getNodesAt(power).stream()
            .allMatch(node -> InfrastructureSavedData.load(level).getConnections(node).isEmpty()), "провод снят с клемм");
        return report(ctx);
    }

    /** Другая реакция без мембраны: вода → катодный и анодный газ, католит не используется. */
    private static int water(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        ElectrolyzerRig.place(level, WATER_CELL, WATER_SIDE);
        check(ctx, ElectrolyzerRig.form(level, WATER_CELL, WATER_SIDE), "машина для воды собрана");
        BlockPos power = ElectrolyzerRig.power(WATER_CELL, WATER_SIDE);
        BlockPos battery = power.above(2);
        level.setBlock(battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState(), Block.UPDATE_ALL);
        wire(level, battery, 0, power, ProcessPowerDevice.PLUS);
        wire(level, battery, 1, power, ProcessPowerDevice.MINUS);
        if (DevicesSavedData.load(level).getDevice(battery) instanceof CreativeBatteryDevice device) {
            device.voltage = 300;
            device.acFrequency = 0;
        }
        IFluidHandler input = level.getCapability(Capabilities.FluidHandler.BLOCK, ElectrolyzerRig.input(WATER_CELL, WATER_SIDE),
            ElectrolyzerRig.worldSide(WATER_SIDE, Direction.EAST));
        check(ctx, input != null && input.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE) == 2000,
            "в порт входа залито 2000 мБ воды");
        return report(ctx);
    }

    private static int waterAssert(CommandContext<CommandSourceStack> ctx) {
        reset();
        ElectrolyzerLogic.State state = ElectrolyzerRig.state(ctx.getSource().getLevel(), WATER_CELL);
        check(ctx, WATER_RECIPE.equals(state.recipeId()), "выбран рецепт воды: " + state.recipeId());
        check(ctx, state.batches >= 1, "прошла хотя бы одна партия без мембраны: " + state.batches);
        check(ctx, state.membrane.getStackInSlot(0).isEmpty(), "мембраны нет, и она не нужна");
        check(ctx, state.cathodeGas.getFluidAmount() == 500 * state.batches, "катодный газ по 500 мБ за партию: " + state.cathodeGas.getFluidAmount());
        check(ctx, state.anodeGas.getFluidAmount() == 250 * state.batches, "анодный газ по 250 мБ за партию: " + state.anodeGas.getFluidAmount());
        check(ctx, state.catholyte.isEmpty(), "католит не используется этим рецептом");
        check(ctx, state.status() != ElectrolyzerStatus.FAULT && state.status() != ElectrolyzerStatus.NO_RECIPE, "статус " + state.status());
        ProcessPowerDevice device = state.device();
        check(ctx, device != null && Math.abs(device.measuredVoltage() - 300) < 30, "питание через клеммы: "
            + (device == null ? "нет" : String.format(java.util.Locale.ROOT, "%.1f В, %.2f А", device.measuredVoltage(), device.measuredCurrent())));
        return report(ctx);
    }

    /** Сохранение состояния: баки, мембрана, прогресс и рецепт переживают запись и чтение NBT мастер-блока. */
    private static int save(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        ElectrolyzerLogic.State state = ElectrolyzerRig.state(level, WATER_CELL);
        state.membrane.setStackInSlot(0, new ItemStack(DestroyExtras.MEMBRANE_ITEM.get()));
        state.progress = 17;
        CompoundTag tag = new CompoundTag();
        state.writeSaveNBT(tag, level.registryAccess());
        ElectrolyzerLogic.State copy = (ElectrolyzerLogic.State) DestroyExtras.ELECTROLYZER.logic()
            .createInitialState(null);
        copy.readSaveNBT(tag, level.registryAccess());
        check(ctx, copy.progress == 17 && copy.batches == state.batches, "прогресс и число партий");
        check(ctx, FluidStack.isSameFluidSameComponents(copy.cathodeGas.getFluid(), state.cathodeGas.getFluid())
            && copy.cathodeGas.getFluidAmount() == state.cathodeGas.getFluidAmount(), "катодный газ с составом");
        check(ctx, copy.input.getFluidAmount() == state.input.getFluidAmount() && copy.mixture.getFluidAmount() == state.mixture.getFluidAmount(),
            "вход и смесь");
        check(ctx, !copy.membrane.getStackInSlot(0).isEmpty(), "мембрана");
        check(ctx, WATER_RECIPE.equals(copy.recipeId()), "id рецепта");
        state.membrane.setStackInSlot(0, ItemStack.EMPTY);
        return report(ctx);
    }

    private static final BlockPos LAVA_CELL = new BlockPos(90, -59, 30);

    /** Жидкость без рецепта: машина пишет «нет рецепта», жидкость сливается из порта входа обратно. */
    private static int lava(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        ElectrolyzerRig.place(level, LAVA_CELL, Direction.NORTH);
        check(ctx, ElectrolyzerRig.form(level, LAVA_CELL, Direction.NORTH), "машина собрана");
        IFluidHandler input = inputPort(level, LAVA_CELL);
        check(ctx, input != null && input.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
            "лава залита во вход");
        return report(ctx);
    }

    private static int lavaAssert(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        ElectrolyzerLogic.State state = ElectrolyzerRig.state(level, LAVA_CELL);
        check(ctx, state.status() == ElectrolyzerStatus.NO_RECIPE, "статус «нет рецепта»: " + state.status());
        check(ctx, state.recipeId() == null && state.mixture.isEmpty(), "рецепт не выбран, смесь пуста");
        FluidStack drained = inputPort(level, LAVA_CELL).drain(1000, IFluidHandler.FluidAction.EXECUTE);
        check(ctx, drained.getFluid() == Fluids.LAVA && drained.getAmount() == 1000, "лава слита из порта входа");
        check(ctx, state.input.isEmpty(), "вход пуст");
        return report(ctx);
    }

    private static IFluidHandler inputPort(ServerLevel level, BlockPos cell) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, ElectrolyzerRig.input(cell, Direction.NORTH),
            ElectrolyzerRig.worldSide(Direction.NORTH, Direction.EAST));
    }

    private static void wire(ServerLevel level, BlockPos a, int nodeA, BlockPos b, int nodeB) {
        for (BlockPos pos : new BlockPos[] {a, b}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ElectricalDeviceBlock<?> block) block.ensureNodesExist(level, pos, state);
        }
        InfrastructureSavedData.load(level).connect(new InWorldNode(nodeA, a), new InWorldNode(nodeB, b), CEEWireTypes.COPPER.get());
    }

    private static void reset() {
        checks = 0;
        failures = 0;
    }

    private static void check(CommandContext<CommandSourceStack> ctx, boolean condition, String label) {
        checks++;
        if (!condition) failures++;
        ctx.getSource().sendSystemMessage(Component.literal("DESTROYEXTRATEST " + (condition ? "OK " : "FAIL ") + label));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        int passed = checks - failures;
        ctx.getSource().sendSystemMessage(Component.literal(
            "DESTROYEXTRATEST " + (failures == 0 && checks > 0 ? "PASS " : "FAIL ") + passed + "/" + checks));
        return failures == 0 && checks > 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
