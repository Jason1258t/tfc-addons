package tfcstudio.betterfluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class IronTankSlabBlock extends IronTankBlock {
    IronTankSlabBlock(Properties properties) {
        super(properties, TankSection.PLATE);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(BlockStateProperties.FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.FACING, rotation.rotate(state.getValue(BlockStateProperties.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BlockStateProperties.FACING, mirror.mirror(state.getValue(BlockStateProperties.FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BlockStateProperties.FACING)) {
            case DOWN -> section.shape;
            case UP -> ShapesByFace.UP;
            case NORTH -> ShapesByFace.NORTH;
            case SOUTH -> ShapesByFace.SOUTH;
            case WEST -> ShapesByFace.WEST;
            case EAST -> ShapesByFace.EAST;
        };
    }

    private static final class ShapesByFace {
        static final VoxelShape UP = box(0, 8, 0, 16, 16, 16);
        static final VoxelShape NORTH = box(0, 0, 0, 16, 16, 8);
        static final VoxelShape SOUTH = box(0, 0, 8, 16, 16, 16);
        static final VoxelShape WEST = box(0, 0, 0, 8, 16, 16);
        static final VoxelShape EAST = box(8, 0, 0, 16, 16, 16);
    }
}
