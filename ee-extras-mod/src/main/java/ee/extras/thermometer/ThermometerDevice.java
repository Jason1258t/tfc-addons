package ee.extras.thermometer;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.dries007.tfc.common.capabilities.BlockCapabilities;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Термометр: 0–1600 °C блока за задней гранью → слабый сигнал 0–15 В EE. */
public class ThermometerDevice extends SimpleElectricalDevice {
    public static final double MAX_TEMPERATURE = 1600;
    public static final double MAX_VOLTAGE = 15;
    public static final double OUTPUT_RESISTANCE = 1000;

    public float temperature;
    public double voltage;

    public ThermometerDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    @Override
    public void preTick(BridgeCollector collector) {
        temperature = readTemperature();
        voltage = Math.max(0, Math.min(MAX_VOLTAGE, temperature / MAX_TEMPERATURE * MAX_VOLTAGE));
        collector.builder(pos).voltageSourceWithResistance(1, 0, OUTPUT_RESISTANCE, voltage);
    }

    private float readTemperature() {
        if (!(level.getBlockState(pos).getBlock() instanceof DirectionalRolledDeviceBlock<?>)) return 0;
        var facing = level.getBlockState(pos).getValue(DirectionalRolledDeviceBlock.FACING);
        BlockPos target = pos.relative(facing.getOpposite());
        IHeatConsumer heat = level.getCapability(BlockCapabilities.HEAT, target, facing);
        return heat == null ? 0 : heat.getTemperature();
    }

    @Override
    public void postTick(SimulationResults results) {}

    @Override
    public void read(CompoundTag tag) {
        temperature = tag.getFloat("Temperature");
        voltage = tag.getDouble("Voltage");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Temperature", temperature);
        tag.putDouble("Voltage", voltage);
    }
}
