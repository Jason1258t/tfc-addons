package ee.extras.machine;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class ElectricDeployerBlock extends DeployerBlock implements MotorMachineBlock {
    public ElectricDeployerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends DeployerBlockEntity> getBlockEntityType() {
        return EeExtras.ELECTRIC_DEPLOYER_BE.get();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public Direction terminalFace(BlockState state) {
        return state.getValue(FACING).getAxis().isVertical() ? Direction.NORTH : Direction.UP;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public SimulatedDeviceType<MotorMachineDevice> getDevice() {
        return EeExtras.ELECTRIC_DEPLOYER_DEVICE.get();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server && !oldState.is(this)) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        ensureNodesExist(level, pos, state);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        removeWiresByPlayer(context.getPlayer(), context.getLevel(), context.getClickedPos());
        return super.onSneakWrenched(state, context);
    }
}
