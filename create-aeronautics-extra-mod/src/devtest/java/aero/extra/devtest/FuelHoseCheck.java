package aero.extra.devtest;

import aero.extra.FuelPumpBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.util.List;
import aero.extra.TransportEngineBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

final class FuelHoseCheck {
    static final BlockPos BASE = new BlockPos(0, 100, 8);

    static int matrix(CommandSourceStack source, String action) {
        ServerLevel level = source.getLevel();
        if (action.equals("matrix")) {
            for (int i = 0; i < 3; i++) {
                BlockPos start = new BlockPos(0, 145, 1000 + i * 4);
                buildLine(level, start, i);
                require(canRun(level, start), "трасса двигателя " + i);
                ((FuelPumpBlockEntity) level.getBlockEntity(start.offset(10, 0, 0))).prime();
            }
            return 1;
        }
        for (var plot : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            for (var holder : plot.getPlot().getLoadedChunks()) {
                for (var blockEntity : List.copyOf(holder.getChunk().getBlockEntities().values())) {
                    if (!(blockEntity instanceof FuelPumpBlockEntity pump)) continue;
                    BlockPos pos = pump.getBlockPos();
                    Direction facing = pump.getBlockState().getValue(BlockStateProperties.FACING);
                    BlockPos enginePos = pos.relative(facing);
                    if (!(level.getBlockEntity(enginePos) instanceof KineticBlockEntity engine)) continue;
                    if (action.equals("plotprime")) pump.prime();
                    if (action.equals("plotcut")) level.destroyBlock(pos.offset(-5, 0, 0), true);
                    BlockPos fanPos = enginePos.relative(Direction.EAST);
                    float load = level.getBlockEntity(fanPos) instanceof KineticBlockEntity fan ? fan.getSpeed() : 0;
                    var fuel = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.offset(-11, 0, 0), Direction.EAST);
                    source.sendSystemMessage(Component.literal(plot.logicalPose().position().y + " "
                            + BuiltInRegistries.BLOCK.getKey(engine.getBlockState().getBlock())
                            + " rpm=" + Math.abs(engine.getSpeed()) + " fan=" + Math.abs(load)
                            + " fuel=" + (fuel == null ? -1 : fuel.getFluidInTank(0).getAmount())
                            + " pos=" + pos + " facing=" + facing
                            + " hose=" + pump.getUpdateTag(level.registryAccess()).getLongArray("HoseEnds").length));
                }
            }
        }
        return 1;
    }

    static int run(CommandSourceStack source, String action) {
        ServerLevel level = source.getLevel();
        if (action.equals("build")) {
            build(level, BASE);
            require(canRun(level, BASE), "участки трассы");
        }
        if (action.equals("break")) {
            level.destroyBlock(BASE.offset(5, 0, 0), true);
        }
        if (action.equals("prime")) {
            ((FuelPumpBlockEntity) level.getBlockEntity(BASE.offset(10, 0, 0))).prime();
        }
        var engine = (TransportEngineBlockEntity) level.getBlockEntity(BASE.offset(11, 0, 0));
        var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, BASE.relative(Direction.WEST), Direction.EAST);
        source.sendSystemMessage(Component.literal("rpm=" + engine.getSpeed() + " fuel=" + tank.getFluidInTank(0).getAmount()));
        return 1;
    }

    static ItemStack hose(int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create_aeronautics_extra:fuel_hose")), count);
    }

    static void build(ServerLevel level, BlockPos origin) {
        buildLine(level, origin, 0);
    }

    private static void buildLine(ServerLevel level, BlockPos origin, int engine) {
        Block port = block("create_aeronautics_extra:fuel_port");
        set(level, origin.relative(Direction.WEST), "create:fluid_tank", null);
        set(level, origin, "create_aeronautics_extra:fuel_port", Direction.WEST);
        var relay = (BooleanProperty) port.getStateDefinition().getProperty("relay");
        level.setBlock(origin.offset(5, 0, 0),
                port.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN).setValue(relay, true),
                Block.UPDATE_ALL);
        boolean externalInlet = engine == 0;
        set(level, origin.offset(10, 0, 0), "create_aeronautics_extra:fuel_pump",
                externalInlet ? Direction.EAST : Direction.UP);
        BlockPos enginePos = origin.offset(externalInlet ? 11 : 10, externalInlet ? 0 : 1, 0);
        String engineId = switch (engine) {
            case 1 -> "simulated:white_portable_engine";
            case 2 -> "createdieselgenerators:diesel_engine";
            default -> "create_aeronautics_extra:transport_engine";
        };
        set(level, enginePos, engineId, Direction.EAST);
        set(level, enginePos.relative(Direction.EAST), "create:encased_fan", Direction.EAST);
        var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, origin.relative(Direction.WEST), Direction.EAST);
        var diesel = BuiltInRegistries.FLUID.get(ResourceLocation.parse("createdieselgenerators:diesel"));
        require(tank.fill(new FluidStack(diesel, 3000), FluidAction.EXECUTE) == 3000, "заполнение исходного бака");
    }

    static boolean canRun(ServerLevel level, BlockPos origin) {
        BlockPos mount = origin.offset(5, 0, 0);
        BlockPos pump = origin.offset(10, 0, 0);
        ItemStack spool = hose(32);
        var player = FakePlayerFactory.getMinecraft(level);
        var prior = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        try {
            use(level, origin);
            use(level, pump);
            require(spool.getCount() == 32, "пролёт 10 м отклонён");
            BlockPos obstacle = origin.offset(2, 0, 0);
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            use(level, origin);
            use(level, mount);
            require(spool.getCount() == 32, "препятствие в пролёте отклонено");
            level.removeBlock(obstacle, false);
            use(level, origin);
            use(level, mount);
            require(spool.getCount() == 27, "первый пролёт списал 5 м рукава");
            use(level, origin);
            use(level, mount);
            require(spool.getCount() == 27, "повтор пролёта отклонён");
            use(level, mount);
            use(level, pump);
            return spool.getCount() == 22;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, prior);
        }
    }

    private static void use(ServerLevel level, BlockPos pos) {
        var player = FakePlayerFactory.getMinecraft(level);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    private static void set(ServerLevel level, BlockPos pos, String id, Direction facing) {
        var state = block(id).defaultBlockState();
        if (facing != null) {
            state = state.setValue(state.hasProperty(BlockStateProperties.FACING)
                    ? BlockStateProperties.FACING : BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void require(boolean condition, String details) {
        if (!condition) throw new IllegalStateException(details);
    }
}
