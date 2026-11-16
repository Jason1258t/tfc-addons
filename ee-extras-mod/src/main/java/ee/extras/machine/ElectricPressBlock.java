package ee.extras.machine;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import ee.extras.EeExtras;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ElectricPressBlock extends MechanicalPressBlock implements ElectricalDeviceBlock<PressMotorDevice> {
    public ElectricPressBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends MechanicalPressBlockEntity> getBlockEntityType() {
        return EeExtras.ELECTRIC_PRESS_BE.get();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public SimulatedDeviceType<PressMotorDevice> getDevice() {
        return EeExtras.ELECTRIC_PRESS_DEVICE.get();
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

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, terminal(state, 0), 1, terminal(state, 1));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        return node == 0 || node == 1 ? terminal(state, node) : null;
    }

    private static Vec3 terminal(BlockState state, int node) {
        Direction right = state.getValue(HORIZONTAL_FACING).getClockWise();
        double offset = node == 0 ? -3.0 / 16 : 3.0 / 16;
        return new Vec3(0.5 + offset * right.getStepX(), 18.0 / 16, 0.5 + offset * right.getStepZ());
    }
}
