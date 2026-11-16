package aero.extra;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

final class FuelPortBlock extends DirectionalBlock {
    static final BooleanProperty RELAY = BooleanProperty.create("relay");
    private static final MapCodec<FuelPortBlock> CODEC = simpleCodec(FuelPortBlock::new);

    FuelPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(RELAY, false));
    }

    @Override
    protected MapCodec<FuelPortBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RELAY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(RELAY, context.isSecondaryUseActive());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case UP -> box(4, 8, 4, 12, 16, 12);
            case DOWN -> box(4, 0, 4, 12, 8, 12);
            case NORTH -> box(4, 4, 0, 12, 12, 8);
            case SOUTH -> box(4, 4, 8, 12, 12, 16);
            case EAST -> box(8, 4, 4, 16, 12, 12);
            case WEST -> box(0, 4, 4, 8, 12, 12);
        };
    }

    static IFluidHandler tank(Level level, BlockPos portPos, BlockState state) {
        if (state.getValue(RELAY)) return null;
        Direction facing = state.getValue(FACING);
        BlockPos tankPos = portPos.relative(facing);
        if (!level.hasChunkAt(tankPos) || !(level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity)) return null;
        return level.getCapability(Capabilities.FluidHandler.BLOCK, tankPos, facing.getOpposite());
    }
}
