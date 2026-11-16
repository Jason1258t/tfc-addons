package ee.extras.motor;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.RMSHolder;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BeltMotorDevice extends SimpleElectricalDevice {
    static final String MOUNT_SIDE = "MountSide";
    public static final IntegratedMotor MODEL = new IntegratedMotor(256);
    public static final SimulatedDeviceType<BeltMotorDevice> TYPE = new SimulatedDeviceType<>(
            EeExtras.rl("belt_motor"), (type, level, pos, data) -> new BeltMotorDevice(level, pos, data, type));

    private final RMSHolder voltage = new RMSHolder(3 * CEEConfigs.server().simulationConfig.microTicks.get());

    private Direction mountSide;
    private boolean reversed;

    private BeltMotorDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    @Override
    public void read(CompoundTag data) {
        mountSide = Direction.byName(data.getString(MOUNT_SIDE));
        reversed = data.getBoolean("Reversed");
    }

    @Override
    public void write(CompoundTag data) {
        if (mountSide != null) data.putString(MOUNT_SIDE, mountSide.getSerializedName());
        data.putBoolean("Reversed", reversed);
    }

    public static boolean supports(BlockState state) {
        return AllBlocks.BELT.has(state) && state.getValue(BeltBlock.PART) == BeltPart.PULLEY
                && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
    }

    public static boolean supportsAuger(BlockState state) {
        // Simulated входит в Aeronautics и остаётся необязательной зависимостью.
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("simulated")
                && (id.getPath().equals("auger_shaft") || id.getPath().equals("auger_cog"));
    }

    public static boolean supportsDrive(BlockState state) {
        return supports(state) || supportsAuger(state);
    }

    public static boolean supportsSide(BlockState state, Direction side) {
        if (!(state.getBlock() instanceof IRotate rotation) || !supportsDrive(state)) return false;
        Direction.Axis axis = rotation.getRotationAxis(state);
        return supports(state) ? side.getAxis() == axis : side.getAxis() != axis;
    }

    public static Vec3 terminal(Direction side, int node) {
        Direction right = side.getAxis().isVertical() ? Direction.EAST : side.getClockWise();
        return Vec3.atCenterOf(BlockPos.ZERO)
                .add(Vec3.atLowerCornerOf(side.getNormal()).scale(7.0 / 16))
                .add(Vec3.atLowerCornerOf(right.getNormal()).scale(node == 0 ? -3.0 / 16 : 3.0 / 16));
    }

    public Direction mountSide() {
        return mountSide;
    }

    public float rpm() {
        return reversed ? -32 : 32;
    }

    public void reverse() {
        reversed = !reversed;
        DevicesSavedData.load((ServerLevel) level).setDirty();
    }

    private BeltMotorHost host() {
        return level.isLoaded(pos) && supportsDrive(level.getBlockState(pos))
                && level.getBlockEntity(pos) instanceof BeltMotorHost host ? host : null;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        BeltMotorHost host = host();
        if (host != null) {
            bridges.builder(pos).resistor(0, 1, MODEL.resistance(host.eeExtras$networkStress(), host.eeExtras$networkCapacity()));
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        BeltMotorHost host = host();
        if (host == null) return;
        double[] first = results.getVoltages(new InWorldNode(0, pos));
        double[] second = results.getVoltages(new InWorldNode(1, pos));
        int samples = Math.min(first.length, second.length);
        for (int i = 0; i < samples; i++) voltage.add(first[i] - second[i]);
        host.eeExtras$readMotorPower(this, samples == 0 ? 0 : voltage.get());
    }

    @Override
    public void onDestroy() {
        BeltMotorHost host = host();
        if (host != null) {
            host.eeExtras$drive(0, 0);
            host.eeExtras$mountSide(null);
        }
        super.onDestroy();
        if (mountSide != null) Block.popResource(level, pos, EeExtras.BELT_MOTOR_ITEM.get().getDefaultInstance());
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !supportsDrive(newState) || (mountSide != null && !supportsSide(newState, mountSide));
    }
}
