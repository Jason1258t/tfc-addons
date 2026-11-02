package ee.extras.converter;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Нагрузка между жилами кабеля (узлы 1 и 2) и FE в блок за задней гранью — формулы те же, что у «заряда»
 * двустороннего преобразователя (BidiConverterDevice) и потребителя EE: сопротивление подбирается по свободному месту
 * в запасе, то есть машина, которой энергия не нужна, не нагружает сеть.
 */
public class CableConverterDevice extends BundledWireTerminationDevice {
    private double charge;
    private double resistance;
    /** Мощность за последний тик, Вт — для очков и /cee device_data */
    private double power;

    public CableConverterDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(pos).resistor(1, 2, resistance == 0 ? BidiConverterDevice.OPEN_R : resistance);
    }

    @Override
    public void postTick(SimulationResults results) {
        double max = BidiConverterDevice.maxEnergy();
        double volts = Math.abs(results.getVoltageAt(pos, 1, 2));
        power = Math.abs(volts * results.getCurrentThrough(pos, 1, 2));
        charge = Mth.clamp(charge + power, 0, max);
        if (volts > 1) resistance = Math.max(20, volts / (Math.max(max - charge, 0.01) / volts));
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CableConverterBlock) give(state.getValue(CableConverterBlock.FACING));
    }

    private void give(Direction facing) {
        BlockPos behind = pos.relative(facing.getOpposite());
        double rate = BidiConverterDevice.rate();
        int give = (int) (charge / rate);
        for (Direction side : BidiConverterDevice.order(facing)) {
            if (give <= 0) return;
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, behind, side);
            if (storage == null || !storage.canReceive()) continue;
            int moved = storage.receiveEnergy(give, false);
            give -= moved;
            charge -= moved * rate;
        }
    }

    @Override
    public void read(CompoundTag tag) {
        charge = tag.getDouble("Charge");
        resistance = tag.getDouble("Resistance");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Charge", charge);
        tag.putDouble("Resistance", resistance);
        tag.putDouble("InputPower", power);
    }

    /** Поворот ключом — тот же блок: иначе EE снял бы узлы и кабели */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
