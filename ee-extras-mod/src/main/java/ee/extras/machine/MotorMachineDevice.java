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

public final class MotorMachineDevice extends SimpleElectricalDevice {
    private final IntegratedMotor motor;
    private final RMSHolder voltage = new RMSHolder(3 * CEEConfigs.server().simulationConfig.microTicks.get());

    public MotorMachineDevice(SimulatedDeviceType<?> type, Level level, BlockPos pos, DevicesSavedData data, double nominalStress) {
        super(level, pos, data, type);
        motor = new IntegratedMotor(nominalStress);
    }

    private MotorMachine machine() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof MotorMachine machine ? machine : null;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        MotorMachine machine = machine();
        if (machine == null || level.hasNeighborSignal(pos)) return;
        float stress = machine.processing() ? machine.drive().requiredStress() : 0;
        bridges.builder(pos).resistor(0, 1, motor.resistance(stress, (float) motor.availableStress(voltage.get())));
    }

    @Override
    public void postTick(SimulationResults results) {
        MotorMachine machine = machine();
        if (machine == null) return;
        double[] first = results.getVoltages(new InWorldNode(0, pos));
        double[] second = results.getVoltages(new InWorldNode(1, pos));
        int samples = Math.min(first.length, second.length);
        for (int i = 0; i < samples; i++) voltage.add(first[i] - second[i]);
        double rms = samples == 0 ? 0 : voltage.get();
        machine.drive().setRunning(!level.hasNeighborSignal(pos) && rms > 0
                && motor.availableStress(rms) >= machine.drive().requiredStress());
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
