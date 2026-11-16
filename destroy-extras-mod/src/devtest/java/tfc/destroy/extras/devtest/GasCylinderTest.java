package tfc.destroy.extras.devtest;

import tfc.destroy.extras.gas.GasFillingStation;
import tfc.destroy.extras.gas.GasPipes;
import petrolpark.mc.destroy.DestroyDataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.mojang.brigadier.Command;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class GasCylinderTest {
    private static final BlockPos DIRECT_SOURCE = new BlockPos(12, 115, 0);
    private static final BlockPos DIRECT_SINK = DIRECT_SOURCE.south(4).above();
    private static final int[] CAPACITIES = {820, 3283, 9851};

    @FunctionalInterface
    public interface TankBuilder {
        void build(ServerLevel level, BlockPos base, Direction face);
    }

    private GasCylinderTest() {}

    public static void commands(RegisterCommandsEvent event, TankBuilder tank,
        BiFunction<String, Integer, FluidStack> gas) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("gas_cylinders_setup").executes(ctx -> run(ctx.getSource(), level -> setup(level, tank, gas))))
            .then(Commands.literal("gas_cylinders_move").executes(ctx -> run(ctx.getSource(), level -> move(level, gas))))
            .then(Commands.literal("gas_cylinders_open").executes(ctx -> run(ctx.getSource(), GasCylinderTest::open)))
            .then(Commands.literal("gas_cylinders_assert").executes(ctx -> run(ctx.getSource(), GasCylinderTest::verify))));
    }

    private static int run(CommandSourceStack source, Consumer<ServerLevel> check) {
        try {
            check.accept(source.getLevel());
            source.sendSuccess(() -> Component.literal("DESTROYEXTRATEST PASS: шесть сценариев баллонов"), false);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal("DESTROYEXTRATEST FAIL: " + e.getMessage()));
            return 0;
        }
    }

    private static void setup(ServerLevel level, TankBuilder tank, BiFunction<String, Integer, FluidStack> gas) {
        tank.build(level, DIRECT_SOURCE, Direction.SOUTH);
        tank.build(level, DIRECT_SINK, Direction.NORTH);
        for (int i = 1; i < 4; i++) {
            level.setBlock(DIRECT_SOURCE.above().south(i), GasPipes.HYDROGEN.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        check(port(level, DIRECT_SOURCE, Direction.SOUTH).fill(gas.apply("hydrogen", 513), FluidAction.EXECUTE) == 513,
            "бак прямой линии не принял водород");

        var items = List.of(GasFillingStation.STEEL, GasFillingStation.STAINLESS, GasFillingStation.BLUE_STEEL);
        for (int i = 0; i < 6; i++) {
            BlockPos source = source(i);
            BlockPos sink = sink(i);
            tank.build(level, source, Direction.SOUTH);
            tank.build(level, sink, Direction.NORTH);
            int capacity = CAPACITIES[i % 3];
            FluidStack supply = gas.apply(species(i), capacity + 100);
            check(port(level, source, Direction.SOUTH).fill(supply, FluidAction.EXECUTE) == capacity + 100,
                "исходный бак не принял газ");
            BlockPos charge = source.above().south();
            station(level, charge, Direction.SOUTH);
            BlockPos deliver = sink.north(2);
            station(level, deliver, Direction.NORTH);
            Block pipe = i < 3 ? GasPipes.HYDROGEN.get() : GasPipes.CHLORINE.get();
            level.setBlock(sink.north(), pipe.defaultBlockState(), Block.UPDATE_ALL);
            insert(level, charge, FakePlayerFactory.getMinecraft(level), new ItemStack(items.get(i % 3).get()));
            check(port(level, charge, Direction.NORTH).fill(supply, FluidAction.EXECUTE) == 0, "заправка без питания");
            power(level, charge);
            power(level, deliver);
        }
    }

    private static void move(ServerLevel level, BiFunction<String, Integer, FluidStack> gas) {
        check(port(level, DIRECT_SINK.above(), Direction.NORTH).getFluidInTank(0).getAmount() == 513
            && port(level, DIRECT_SOURCE.above(), Direction.SOUTH).getFluidInTank(0).isEmpty(),
            "бак не подал газ без баллона");
        var player = FakePlayerFactory.getMinecraft(level);
        for (int i = 0; i < 6; i++) {
            BlockPos charge = source(i).above().south();
            BlockPos deliver = sink(i).north(2);
            int capacity = CAPACITIES[i % 3];
            assertStored(port(level, charge, Direction.NORTH), i, capacity);
            check(port(level, source(i).above(), Direction.SOUTH).getFluidInTank(0).getAmount() == 100,
                "газ потерян при переполнении");
            check(port(level, charge, Direction.NORTH).fill(gas.apply(species(i), 1), FluidAction.SIMULATE) == 0, "перелив выше предела");
            player.getInventory().clearContent();
            interact(level, charge, player);
            check(port(level, charge, Direction.NORTH).getFluidInTank(0).isEmpty(), "запас остался в узле после изъятия");
            insert(level, deliver, player, player.getInventory().getItem(0));
            assertStored(port(level, deliver, Direction.SOUTH), i, capacity);
        }
    }

    private static void open(ServerLevel level) {
        var player = FakePlayerFactory.getMinecraft(level);
        player.setShiftKeyDown(true);
        try {
            for (int i = 0; i < 6; i++) {
                BlockPos deliver = sink(i).north(2);
                assertStored(port(level, deliver, Direction.SOUTH), i, CAPACITIES[i % 3]);
                interact(level, deliver, player);
            }
        } finally {
            player.setShiftKeyDown(false);
        }
    }

    private static void verify(ServerLevel level) {
        var player = FakePlayerFactory.getMinecraft(level);
        for (int i = 0; i < 6; i++) {
            BlockPos deliver = sink(i).north(2);
            check(port(level, deliver, Direction.SOUTH).getFluidInTank(0).isEmpty(), "баллон не опустел");
            var received = port(level, sink(i).above(), Direction.NORTH).getFluidInTank(0);
            check(received.getAmount() == CAPACITIES[i % 3] && speciesMatches(received, i), "газ потерян при выдаче");
            interact(level, deliver, player);
            player.getInventory().clearContent();
        }
    }

    private static void assertStored(IFluidHandler station, int i, int amount) {
        var gas = station.getFluidInTank(0);
        check(gas.getAmount() == amount && speciesMatches(gas, i), "неверный запас баллона " + i + ": " + gas.getAmount());
    }

    private static boolean speciesMatches(FluidStack gas, int i) {
        var mixture = gas.get(DestroyDataComponents.MIXTURE);
        return mixture != null && mixture.getList("Contents", 10).getCompound(0)
            .getString("Molecule").equals("destroy:" + species(i));
    }

    private static String species(int i) {
        return i < 3 ? "hydrogen" : "chlorine";
    }

    private static BlockPos source(int i) {
        return new BlockPos(-12 + 4 * i, 115, 0);
    }

    private static BlockPos sink(int i) {
        return source(i).south(8);
    }

    private static IFluidHandler port(ServerLevel level, BlockPos pos, Direction face) {
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        check(port != null, "нет порта " + pos);
        return port;
    }

    private static void station(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(pos, GasFillingStation.BLOCK.get().defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), Block.UPDATE_ALL);
    }

    private static void insert(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        check(level.getBlockState(pos).useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit(pos)).consumesAction(),
            "узел не принял баллон");
    }

    private static void interact(ServerLevel level, BlockPos pos, Player player) {
        check(level.getBlockState(pos).useWithoutItem(level, player, hit(pos)).consumesAction(),
            "узел не отвечает на взаимодействие");
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static void power(ServerLevel level, BlockPos pos) {
        BlockPos battery = pos.above(2);
        level.setBlock(battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState(), Block.UPDATE_ALL);
        for (BlockPos endpoint : new BlockPos[] {pos, battery}) {
            var state = level.getBlockState(endpoint);
            ((ElectricalDeviceBlock<?>) state.getBlock()).ensureNodesExist(level, endpoint, state);
        }
        var devices = DevicesSavedData.load(level);
        var source = devices.getDevice(battery, CreativeBatteryDevice.class);
        check(source != null, "нет батареи");
        source.voltage = 300;
        source.acFrequency = 0;
        devices.setDirty();
        for (int node = 0; node < 2; node++) {
            InfrastructureSavedData.load(level).connect(new InWorldNode(node, battery), new InWorldNode(node, pos),
                CEEWireTypes.COPPER.get());
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
