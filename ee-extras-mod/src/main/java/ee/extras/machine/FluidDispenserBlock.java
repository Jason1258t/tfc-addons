package ee.extras.machine;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FluidDispenserBlock extends HorizontalDirectionalBlock implements IBE<FluidDispenserBlockEntity> {
    public static final MapCodec<FluidDispenserBlock> CODEC = simpleCodec(FluidDispenserBlock::new);
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);

    public FluidDispenserBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<FluidDispenserBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<FluidDispenserBlockEntity> getBlockEntityClass() {
        return FluidDispenserBlockEntity.class;
    }

    @Override
    public BlockEntityType<FluidDispenserBlockEntity> getBlockEntityType() {
        return EeExtras.FLUID_DISPENSER_BE.get();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next,
            boolean moved) {
        IBE.onRemove(state, level, pos, next);
        super.onRemove(state, level, pos, next, moved);
    }
}
