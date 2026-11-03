package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Двухполюсная нагрузка EE. Simulation thread видит только неизменяемый снимок параметров рецепта. */
public final class PcbMachineDevice extends SimpleElectricalDevice {
    public static final int PLUS = 0;
    public static final int MINUS = 1;
    private static final double OPEN_RESISTANCE = 1_000_000_000;

    private volatile Load load = new Load(0, 1, false);
    private volatile double measuredVoltage;
    private volatile double measuredCurrent;

    public PcbMachineDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    public void setLoad(double voltage, double current, boolean active) {
        load = new Load(voltage, Math.max(0.001, current), active);
    }

    @Override
    public void preTick(BridgeCollector collector) {
        Load current = load;
        collector.builder(pos).resistor(PLUS, MINUS,
            current.active && current.voltage > 0 ? current.voltage / current.current : OPEN_RESISTANCE);
    }

    @Override
    public void postTick(SimulationResults results) {
        measuredVoltage = Math.abs(results.getVoltageAt(pos, PLUS, MINUS));
        measuredCurrent = Math.abs(results.getCurrentThrough(pos, PLUS, MINUS));
    }

    public boolean hasWorkingPower() {
        Load current = load;
        double voltage = measuredVoltage;
        return current.active && current.voltage > 0
            && voltage >= current.voltage * 0.9 && voltage <= current.voltage * 1.1
            && Math.abs(measuredCurrent - current.current) <= 0.1;
    }

    public double measuredVoltage() {
        return measuredVoltage;
    }

    public double measuredCurrent() {
        return measuredCurrent;
    }

    @Override
    public void read(CompoundTag tag) {
        load = new Load(tag.getDouble("Voltage"), Math.max(0.001, tag.getDouble("Current")),
            tag.getBoolean("Active"));
    }

    @Override
    public void write(CompoundTag tag) {
        Load current = load;
        tag.putDouble("Voltage", current.voltage);
        tag.putDouble("Current", current.current);
        tag.putBoolean("Active", current.active);
    }

    private record Load(double voltage, double current, boolean active) {}
}
