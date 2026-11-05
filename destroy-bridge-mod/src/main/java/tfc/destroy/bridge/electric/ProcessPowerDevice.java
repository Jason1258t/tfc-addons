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
    private volatile double nominalVoltage = NOMINAL_VOLTAGE;
    private volatile double nominalCurrent = NOMINAL_CURRENT;
    private volatile double measuredVoltage;
    private volatile double measuredCurrent;

    public ProcessPowerDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    public void setActive(boolean value) {
        active = value;
    }

    /** Другие машины моста задают собственные 240/600 В без нового типа EE-устройства. */
    public void setRating(double voltage, double current) {
        if (voltage <= 0 || current <= 0) throw new IllegalArgumentException("Номиналы нагрузки должны быть положительными");
        nominalVoltage = voltage;
        nominalCurrent = current;
    }

    @Override
    public void preTick(BridgeCollector collector) {
        collector.builder(pos).resistor(PLUS, MINUS, active ? nominalVoltage / nominalCurrent : OPEN_RESISTANCE);
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
        return voltage >= nominalVoltage * 0.9 && voltage <= nominalVoltage * 1.1
            && Math.abs(measuredCurrent - nominalCurrent) <= Math.max(0.1, nominalCurrent * 0.0125);
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
