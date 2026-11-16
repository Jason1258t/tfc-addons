package tfc.destroy.extras.gas;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class GasValveBlock extends GasPipeBlock {
    private static final MapCodec<GasValveBlock> CODEC = simpleCodec(GasValveBlock::new);
    private static final VoxelShape SHAPE = box(4, 3, 4, 12, 13, 12);

    GasValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y)
            .setValue(BlockStateProperties.OPEN, true));
    }

    @Override
    protected MapCodec<? extends GasPipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.AXIS, BlockStateProperties.OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
        BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        var axis = state.getValue(BlockStateProperties.AXIS);
        return state.setValue(BlockStateProperties.AXIS, rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, axis)).getAxis());
    }

    @Override
    boolean hasPort(BlockState state, Direction side) {
        return side == null || side.getAxis() == state.getValue(BlockStateProperties.AXIS);
    }

    @Override
    boolean permitsFlow(BlockState state, Direction side) {
        return state.getValue(BlockStateProperties.OPEN) && hasPort(state, side);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean open = !state.getValue(BlockStateProperties.OPEN);
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable(open ? "message.destroy_extras.gas_valve.open"
                : "message.destroy_extras.gas_valve.closed"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
