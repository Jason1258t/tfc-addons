package tfcstudio.betterfluids;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

class IronTankBlock extends FluidTankBlock {
    final TankSection section;

    IronTankBlock(Properties properties) {
        this(properties, TankSection.FULL);
    }

    IronTankBlock(Properties properties, TankSection section) {
        super(properties, false);
        this.section = section;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
        net.minecraft.world.level.BlockGetter level, BlockPos pos,
        net.minecraft.world.phys.shapes.CollisionContext context) {
        return section.shape;
    }

    @Override
    public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
        return BetterFluids.TANK_ENTITY.get();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && state.getBlock() != oldState.getBlock()
            && level.getBlockEntity(pos) instanceof IronTankEntity tank) {
            tank.updateNeighborhood();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() == newState.getBlock()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof IronTankEntity tank && tank.legacy()) {
            super.onRemove(state, level, pos, newState, moving);
        } else {
            level.removeBlockEntity(pos);
        }
        if (!level.isClientSide) {
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                if (level.hasChunkAt(neighborPos) && level.getBlockEntity(neighborPos) instanceof IronTankEntity neighbor) {
                    neighbor.neighborRemoved(direction.getOpposite());
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof IronTankEntity tank) {
            tank.updateNetworkComparators();
        }
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IronTankEntity tank && !tank.legacy()) {
            return ComparatorUtil.fractionToRedstoneLevel(tank.networkFillFraction());
        }
        return super.getAnalogOutputSignal(state, level, pos);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (section == TankSection.FULL && context.getClickedFace().getAxis().isVertical()) {
            return super.onWrenched(state, context);
        }
        Level level = context.getLevel();
        if (!level.isClientSide && level.getBlockEntity(context.getClickedPos()) instanceof IronTankEntity tank) {
            tank.toggleConnections();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
