package ee.extras.machine;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.RMSHolder;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import ee.extras.motor.IntegratedMotor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PressMotorDevice extends SimpleElectricalDevice {
    private final IntegratedMotor motor = new IntegratedMotor(256);
    private final RMSHolder voltage = new RMSHolder(3 * CEEConfigs.server().simulationConfig.microTicks.get());

    public PressMotorDevice(SimulatedDeviceType<?> type, Level level, BlockPos pos, DevicesSavedData data) {
        super(level, pos, data, type);
    }

    private ElectricPressBlockEntity press() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ElectricPressBlockEntity press ? press : null;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        ElectricPressBlockEntity press = press();
        if (press == null || !press.drive().enabled()) return;
        float stress = press.processing() ? press.requiredStress() : 0;
        bridges.builder(pos).resistor(0, 1, motor.resistance(stress, (float) motor.availableStress(voltage.get())));
    }

    @Override
    public void postTick(SimulationResults results) {
        ElectricPressBlockEntity press = press();
        if (press == null) return;
        double[] first = results.getVoltages(new InWorldNode(0, pos));
        double[] second = results.getVoltages(new InWorldNode(1, pos));
        int samples = Math.min(first.length, second.length);
        for (int i = 0; i < samples; i++) voltage.add(first[i] - second[i]);
        double rms = samples == 0 ? 0 : voltage.get();
        press.setMotorRunning(press.drive().enabled() && rms > 0
                && motor.availableStress(rms) >= press.requiredStress());
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
