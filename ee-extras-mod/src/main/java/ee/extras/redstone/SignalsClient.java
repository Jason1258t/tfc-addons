package ee.extras.redstone;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Предпросмотр скрытых сигнальных контактов, отдельно от силовых клемм EE. */
public final class SignalsClient {
    public static void register(IEventBus bus) {
        bus.addListener(SignalsClient::tick);
        bus.addListener(SignalsClient::tooltip);
    }
    private static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.is(Signals.SPOOL.get()) || stack.is(Signals.CONNECTOR_ITEM.get()))
            event.getToolTip().add(Component.translatable("ee_extras.signal." + (stack.is(Signals.SPOOL.get()) ? "spool_help" : "connector_help")));
    }
    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        for (var hand : InteractionHand.values()) {
            var stack = mc.player.getItemInHand(hand);
            if (!stack.is(Signals.SPOOL.get())) continue;
            ElectricPropertiesOverlay.INSTANCE.removeHoveredNode();
            var first = stack.get(CEEDataComponents.SELECTED_NODE);
            var from = first != null && Signals.valid(mc.level, first) ? first.getPosition(mc.level) : null;
            var target = mc.hitResult instanceof BlockHitResult hit
                    ? Signals.hit(mc.level, hit.getBlockPos(), hit.getDirection(), hit.getLocation()) : null;
            var to = target == null ? null : target.getPosition(mc.level);
            if (to != null) Outliner.getInstance().showAABB("ee_signal_hover", new AABB(to,to).inflate(.09))
                    .colored(0xDC3434).lineWidth(.025f);
            if (from != null) {
                Outliner.getInstance().showAABB("ee_signal_selected", new AABB(from,from).inflate(.1))
                        .colored(0xFFD166).lineWidth(.025f);
                if (to != null && from.distanceTo(to) <= 32) Outliner.getInstance().showLine("ee_signal_preview",from,to)
                        .colored(0xDC3434).lineWidth(.04f).disableLineNormals();
            }
        }
    }
    private SignalsClient() {}
}
