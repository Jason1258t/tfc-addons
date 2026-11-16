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
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BeltMotorDevice extends SimpleElectricalDevice {
    public static final IntegratedMotor MODEL = new IntegratedMotor(256);
    public static final SimulatedDeviceType<BeltMotorDevice> TYPE = new SimulatedDeviceType<>(
            EeExtras.rl("belt_motor"), (type, level, pos, data) -> new BeltMotorDevice(level, pos, data, type));

    private final RMSHolder voltage = new RMSHolder(3 * CEEConfigs.server().simulationConfig.microTicks.get());

    private BeltMotorDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    public static boolean supports(BlockState state) {
        return AllBlocks.BELT.has(state) && state.getValue(BeltBlock.PART) == BeltPart.PULLEY
                && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
    }

    private BeltMotorHost host() {
        return level.isLoaded(pos) && supports(level.getBlockState(pos))
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
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !supports(newState);
    }
}
