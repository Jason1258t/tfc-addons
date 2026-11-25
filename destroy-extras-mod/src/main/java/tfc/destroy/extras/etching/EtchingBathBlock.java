package tfc.destroy.extras.etching;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class EtchingBathBlock extends Block implements EntityBlock, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public EtchingBathBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public BlockState getRotatedBlockState(BlockState state, Direction axis) {
        return state.setValue(FACING, state.getValue(FACING).getClockWise());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
        BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EtchingBathBlockEntity bath))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, bath)
            || FluidHelper.tryFillItemFromBE(level, player, hand, stack, bath))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (GenericItemEmptying.canItemBeEmptied(level, stack)
            || GenericItemFilling.canItemBeFilled(level, stack))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        var remaining = ItemHandlerHelper.insertItem(bath.items, stack.copyWithCount(1), true);
        if (!remaining.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            ItemHandlerHelper.insertItem(bath.items, stack.copyWithCount(1), false);
            if (!player.isCreative()) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EtchingBathBlockEntity bath)) return InteractionResult.PASS;
        int[] slots = player.isShiftKeyDown() ? new int[] {1, 0, 3, 4, 5, 2} : new int[] {2, 0, 3, 4, 5, 1};
        for (int slot : slots) {
            if (bath.items.getStackInSlot(slot).isEmpty()) continue;
            if (!level.isClientSide) player.getInventory().placeItemBackInInventory(bath.items.extractItem(slot, 1, false));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EtchingBathBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, entity) -> {
            if (entity instanceof EtchingBathBlockEntity bath) bath.tick();
        };
    }
}
