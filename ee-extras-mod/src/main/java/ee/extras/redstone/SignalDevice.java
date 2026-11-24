package ee.extras.redstone;

import com.george_vi.electroenergetics.content.connector.InsulatorDevice;
import com.george_vi.electroenergetics.devices.device.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Настройка сигнального коннектора хранится вместе с устройствами EE. */
public class SignalDevice extends InsulatorDevice {
    public int channel;
    public boolean output;
    public int signal;
    public boolean needsNotify = true;

    public SignalDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    @Override public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override public void write(CompoundTag tag) {
        tag.putInt("Channel", channel);
        tag.putBoolean("Output", output);
        // Выход пересчитывается после загрузки, устаревший сигнал не сохраняем.
    }

    @Override public void read(CompoundTag tag) {
        channel = Mth.clamp(tag.getInt("Channel"), 0, 15);
        output = tag.getBoolean("Output");
        signal = 0;
        needsNotify = true;
    }
}
