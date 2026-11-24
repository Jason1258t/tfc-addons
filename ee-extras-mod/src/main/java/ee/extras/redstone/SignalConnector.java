package ee.extras.redstone;

import com.george_vi.electroenergetics.content.connector.*;
import com.george_vi.electroenergetics.devices.device.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

/** Один изолированный сигнальный контакт, не принимающий силовые провода. */
public class SignalConnector extends InsulatorBlock {
    public SignalConnector(Properties properties) { super(properties); }

    @Override public SimulatedDeviceType<InsulatorDevice> getDevice() { return Signals.DEVICE.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(Signals.BASE, point(state));
    }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == Signals.BASE ? point(state) : null;
    }
    private Vec3 point(BlockState state) {
        return new Vec3(.5, .5, .5);
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return com.george_vi.electroenergetics.CEEShapes.CONNECTOR.get(state.getValue(FACING));
    }
    @Override public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) { return false; }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level instanceof ServerLevel sl) {
            var device = DevicesSavedData.load(sl).getDevice(pos, SignalDevice.class);
            if (device != null && device.output) return device.signal;
        }
        return 0;
    }
    @Override protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING) ? getSignal(state, level, pos, direction) : 0;
    }
}
