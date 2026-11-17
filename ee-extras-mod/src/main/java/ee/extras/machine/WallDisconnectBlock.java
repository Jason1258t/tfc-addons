package ee.extras.machine;

import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class WallDisconnectBlock extends CutOffSwitchBlock {
    public WallDisconnectBlock(Properties properties) {
        super(properties, false);
        registerDefaultState(defaultBlockState().setValue(CLOSED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction front = state.getValue(FACING);
        BlockPos support = pos.relative(front.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, front);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return state.getFluidState().createLegacyBlock();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Катушка и ключ должны проходить через обычное взаимодействие EE.
        if (!state.getValue(CLOSED) && level.hasNeighborSignal(pos)) {
            if (stack.isEmpty()) return ItemInteractionResult.SUCCESS;
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemInteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (result.consumesAction() && level instanceof ServerLevel server) {
            DevicesSavedData.load(server).setDirty();
        }
        return result;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (level instanceof ServerLevel server && state.getValue(CLOSED) && level.hasNeighborSignal(pos)) {
            DevicesSavedData devices = DevicesSavedData.load(server);
            CutOffSwitchDevice device = devices.getDevice(pos, CutOffSwitchDevice.class);
            if (device != null) {
                device.isClosed = false;
                devices.setDirty();
            }
            level.setBlockAndUpdate(pos, state.setValue(CLOSED, false));
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        }
    }
}
