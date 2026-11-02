package ee.extras.lantern;

import blusunrize.immersiveengineering.common.blocks.metal.FloodlightBlockEntity;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Прожектор в сети EE: нагрузка между жилами кабеля (узлы 1 и 2), пока он светит; выключенный редстоуном не потребляет.
 * Включением по-прежнему правит блок-сущность IE (редстоун, отвёртка, компьютер, задержка 20 тиков); устройство только
 * держит её запас полным, пока на жилах есть напряжение: на переменном токе мгновенное напряжение проходит через ноль,
 * поэтому запас подпитывается ещё HOLD тиков после последнего замера (иначе мигал бы, как и фонарь — LanternDevice).
 */
public class FloodlightDevice extends SimpleElectricalDevice {
    /** Сопротивление, Ом: 230 В — 120 Вт = 5 FE/т прожектора IE при 24 Вт·т/FE (настройки сборки) */
    static final double RESISTANCE = 440;
    /** Выключен: обрыв, как у преобразователя (BidiConverterDevice.OPEN_R) */
    static final double OPEN_R = 999999;
    /** С какого напряжения светит, В — как фонарь */
    static final double MIN_VOLTS = LanternDevice.MIN_VOLTS;
    static final int HOLD = LanternDevice.HOLD;

    private boolean load;
    private int hold;
    /** Последний замер — для /cee device_data */
    private double volts;

    public FloodlightDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(pos).resistor(1, 2, load ? RESISTANCE : OPEN_R);
    }

    @Override
    public void postTick(SimulationResults results) {
        volts = Math.abs(results.getVoltageAt(pos, 1, 2));
        if (volts >= MIN_VOLTS) hold = HOLD;
        else if (hold > 0) hold--;
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof FloodlightBlockEntity be)) return;
        if (hold > 0) be.energyStorage = be.maximumStorage;
        load = be.getIsActive();
    }

    @Override
    public void read(CompoundTag tag) {
        load = tag.getBoolean("Load");
        hold = tag.getInt("Hold");
        volts = tag.getDouble("Volts");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Load", load);
        tag.putInt("Hold", hold);
        tag.putDouble("Volts", volts);
        tag.putDouble("Watts", load ? volts * volts / RESISTANCE : 0);
    }

    /** Смена состояния (active) — тот же прожектор: иначе EE снял бы узлы и кабели */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
