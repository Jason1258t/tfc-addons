package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/** Каждый Display Link выбирает собственный канал аппаратного адаптера. */
final class PcbCreateSource extends SingleLineDisplaySource {
    private final boolean digital;

    PcbCreateSource(boolean digital) {
        this.digital = digital;
    }

    @Override
    public Component getName() {
        return Component.translatable(digital ? "ee_extras.pcb.create_state" : "ee_extras.pcb.create_voltage");
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.level() instanceof ServerLevel level)) return Component.empty();
        var device = DevicesSavedData.load(level).getDevice(context.getSourcePos(), PcbCircuitDevice.class);
        var adapter = device == null ? null : device.createInterface(context.sourceConfig().getInt("Adapter"));
        if (adapter == null || !adapter.powered()) return Component.empty();
        return Component.literal(digital ? adapter.high() ? "1" : "0"
                : String.format(Locale.ROOT, "%.2f", adapter.analog()));
    }

    @Override
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context,
            com.simibubi.create.foundation.gui.ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (!isFirstLine) {
            var options = java.util.stream.IntStream.rangeClosed(1, 64)
                    .mapToObj(number -> Component.translatable("ee_extras.pcb.create_adapter", number)).toList();
            builder.addSelectionScrollInput(0, 120,
                    (input, label) -> input.forOptions(options)
                            .titled(Component.translatable("ee_extras.pcb.create_adapter_title")), "Adapter");
        }
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return false;
    }
}
