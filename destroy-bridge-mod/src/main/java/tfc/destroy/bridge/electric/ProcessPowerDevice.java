package tfc.destroy.bridge.electric;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Двухполюсная нагрузка EE для компактного процесса 300 В / 8 А. Основной поток задаёт только active; simulation thread
 * строит цепь и публикует измерения. Здесь намеренно нет обращений к блокам, рецептам или миру.
 */
public final class ProcessPowerDevice extends SimpleElectricalDevice {
    public static final double NOMINAL_VOLTAGE = 300;
    public static final double NOMINAL_CURRENT = 8;
    public static final double LOAD_RESISTANCE = NOMINAL_VOLTAGE / NOMINAL_CURRENT;
    public static final double OPEN_RESISTANCE = 1_000_000_000;
    public static final int PLUS = 0;
    public static final int MINUS = 1;

    private volatile boolean active;
    private volatile double measuredVoltage;
    private volatile double measuredCurrent;

    public ProcessPowerDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    public void setActive(boolean value) {
        active = value;
    }

    @Override
    public void preTick(BridgeCollector collector) {
        collector.builder(pos).resistor(PLUS, MINUS, active ? LOAD_RESISTANCE : OPEN_RESISTANCE);
    }

    @Override
    public void postTick(SimulationResults results) {
        measuredVoltage = Math.abs(results.getVoltageAt(pos, PLUS, MINUS));
        measuredCurrent = Math.abs(results.getCurrentThrough(pos, PLUS, MINUS));
    }

    public double measuredVoltage() {
        return measuredVoltage;
    }

    public double measuredCurrent() {
        return measuredCurrent;
    }

    public double measuredPower() {
        return measuredVoltage * measuredCurrent;
    }

    public boolean hasWorkingPower() {
        double voltage = measuredVoltage;
        return voltage >= NOMINAL_VOLTAGE * 0.9 && voltage <= NOMINAL_VOLTAGE * 1.1
            && Math.abs(measuredCurrent - NOMINAL_CURRENT) <= 0.1;
    }

    @Override
    public void read(CompoundTag tag) {
        active = tag.getBoolean("Active");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Active", active);
    }
}
