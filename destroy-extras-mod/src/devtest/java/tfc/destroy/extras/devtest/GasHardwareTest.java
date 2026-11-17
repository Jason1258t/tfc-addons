package tfc.destroy.extras.devtest;

import com.mojang.brigadier.Command;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import petrolpark.mc.destroy.DestroyDataComponents;
import tfc.destroy.extras.gas.GasFillingStation;
import tfc.destroy.extras.gas.GasPipes;

final class GasHardwareTest {
    private static final BlockPos CYLINDER = new BlockPos(9, 123, 5);
    private static final BlockPos VALVE = CYLINDER.above(2);
    private static final BlockPos REMOUNT = CYLINDER.east(4);
    private static final BlockPos RECEIVER = REMOUNT.south(4);

    private GasHardwareTest() {}

    static void commands(RegisterCommandsEvent event, BiFunction<String, Integer, FluidStack> gas) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("gas_hardware").requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx.getSource(), level -> verify(level, gas), "вентиль, четыре газа, перенос баллона и плотность")))
            .then(Commands.literal("gas_hardware_start_output").requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx.getSource(), GasHardwareTest::startOutput, "кислород в установленном баллоне сохранён")))
            .then(Commands.literal("gas_hardware_check_output").requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx.getSource(), GasHardwareTest::checkOutput, "кислород полностью прошёл в приёмный баллон"))));
    }

    private static int run(CommandSourceStack source, Consumer<ServerLevel> command, String success) {
        try {
            command.accept(source.getLevel());
            source.sendSuccess(() -> Component.literal("DESTROYEXTRATEST PASS: " + success), false);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal("DESTROYEXTRATEST FAIL: " + e.getMessage()));
            return 0;
        }
    }

    private static void verify(ServerLevel level, BiFunction<String, Integer, FluidStack> gas) {
        var player = FakePlayerFactory.getMinecraft(level);
        placeCylinder(level, player, CYLINDER, new ItemStack(GasFillingStation.STEEL.get()));
        level.setBlock(VALVE.below(), GasPipes.HYDROGEN.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(VALVE, GasPipes.VALVE.get().defaultBlockState().setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
        level.setBlock(VALVE.above(), GasPipes.PIPE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(VALVE.east(), GasPipes.PIPE.get().defaultBlockState(), Block.UPDATE_ALL);
        var inlet = port(level, VALVE.above(), Direction.UP);
        var sideways = port(level, VALVE.east(), Direction.EAST);
        FluidStack nitrogen = gas.apply("nitrogen", 64);
        var data = nitrogen.get(DestroyDataComponents.MIXTURE).copy();
        data.getList("Contents", 10).getCompound(0).putFloat("Concentration", 0.125f);
        nitrogen.set(DestroyDataComponents.MIXTURE, data);
        check(inlet.fill(nitrogen, FluidAction.EXECUTE) == 0, "закрытый вентиль пропустил азот");
        click(level, VALVE, player);
        check(sideways.fill(nitrogen, FluidAction.EXECUTE) == 0, "вентиль пропустил газ через бок");
        check(inlet.fill(nitrogen, FluidAction.SIMULATE) == 64, "открытый вентиль не пропустил симуляцию");
        check(port(level, CYLINDER, Direction.UP).getFluidInTank(0).isEmpty(), "симуляция изменила цилиндр");
        check(inlet.fill(nitrogen, FluidAction.EXECUTE) == 64, "открытый вентиль не пропустил азот");
        check(inlet.fill(gas.apply("oxygen", 8), FluidAction.EXECUTE) == 0, "газы смешались в баллоне");
        check(inlet.fill(gas.apply("nitrogen", 64), FluidAction.EXECUTE) == 64, "баллон не принял азот другой плотности");
        var state = level.getBlockState(CYLINDER);
        var drops = Block.getDrops(state, level, CYLINDER, level.getBlockEntity(CYLINDER), player, new ItemStack(Items.IRON_PICKAXE));
        check(drops.size() == 1, "из баллона выпало не ровно по одному предмету");
        level.removeBlock(CYLINDER, false);
        placeCylinder(level, player, REMOUNT, drops.getFirst());
        var tank = port(level, REMOUNT, Direction.UP);
        FluidStack stored = tank.getFluidInTank(0);
        check(stored.getAmount() == 128, "перенос баллона потерял объём");
        var mixture = stored.get(DestroyDataComponents.MIXTURE).getList("Contents", 10).getCompound(0);
        check(mixture.getString("Molecule").equals("destroy:nitrogen") && mixture.getFloat("Concentration") == 0.5625f,
            "перенос баллона потерял вещество");
        check(tank.drain(128, FluidAction.EXECUTE).isEmpty(), "баллон выдал газ в режиме заправки");
        toggle(level, REMOUNT, player);
        var output = port(level, REMOUNT, Direction.UP);
        check(output.drain(128, FluidAction.EXECUTE).getAmount() == 128, "баллон не выдал газ в режиме выдачи");
        toggle(level, REMOUNT, player);
        for (String species : new String[]{"oxygen", "hydrogen", "chlorine"}) {
            placeCylinder(level, player, CYLINDER, new ItemStack(GasFillingStation.STEEL.get()));
            check(inlet.fill(gas.apply(species, 32), FluidAction.EXECUTE) == 32, "не прошёл " + species);
            level.removeBlock(CYLINDER, false);
        }

        check(port(level, REMOUNT, Direction.UP).fill(gas.apply("oxygen", 513), FluidAction.EXECUTE) == 513,
            "баллон не принял кислород для выдачи");
        placeCylinder(level, player, RECEIVER, new ItemStack(GasFillingStation.STEEL.get()));
        for (int segment = 0; segment <= 4; segment++) {
            level.setBlock(REMOUNT.above().south(segment), GasPipes.PIPE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(REMOUNT.above().south(2), GasPipes.VALVE.get().defaultBlockState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), Block.UPDATE_ALL);
    }

    private static void startOutput(ServerLevel level) {
        check(port(level, REMOUNT, Direction.UP).getFluidInTank(0).getAmount() == 513,
            "запас установленного баллона не сохранился");
        toggle(level, REMOUNT, FakePlayerFactory.getMinecraft(level));
    }

    private static void checkOutput(ServerLevel level) {
        FluidStack received = port(level, RECEIVER, Direction.UP).getFluidInTank(0);
        check(received.getAmount() == 513 && port(level, REMOUNT, Direction.UP).getFluidInTank(0).isEmpty(),
            "полная выдача из установленного баллона не прошла");
    }

    private static void placeCylinder(ServerLevel level, FakePlayer player, BlockPos pos, ItemStack stack) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
        check(stack.getItem() instanceof BlockItem item
            && item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)).consumesAction(),
            "предмет не ставится в мир");
    }

    private static void toggle(ServerLevel level, BlockPos pos, FakePlayer player) {
        player.setShiftKeyDown(true);
        try {
            click(level, pos, player);
        } finally {
            player.setShiftKeyDown(false);
        }
    }

    private static void click(ServerLevel level, BlockPos pos, FakePlayer player) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        check(level.getBlockState(pos).useWithoutItem(level, player, hit).consumesAction(), "ПКМ не сработал");
    }

    private static IFluidHandler port(ServerLevel level, BlockPos pos, Direction side) {
        var capability = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        check(capability != null, "нет газового порта " + pos + " " + side);
        return capability;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
