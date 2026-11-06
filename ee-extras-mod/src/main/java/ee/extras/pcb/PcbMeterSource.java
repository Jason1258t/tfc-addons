package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

final class PcbMeterSource extends SingleLineDisplaySource {
    @Override
    public Component getName() {
        return Component.translatable("ee_extras.pcb.meter");
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (context.level() instanceof ServerLevel level) {
            var device = DevicesSavedData.load(level).getDevice(context.getSourcePos(), PcbCircuitDevice.class);
            if (device != null && device.meterKind() != null) {
                String value = String.format(Locale.ROOT, "%.2f", device.meterValue());
                return Component.translatable(device.meterKind() == PcbCircuitDevice.Kind.AMMETER
                        ? "ee_extras.pcb.meter_amps" : "ee_extras.pcb.meter_volts", value);
            }
        }
        return Component.empty();
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }
}
