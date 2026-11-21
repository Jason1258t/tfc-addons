package ee.extras.interaction;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireItem;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import ee.extras.EeExtras;
import ee.extras.lines.Tension;
import ee.extras.panel.PanelModules;
import ee.extras.wire.SurfaceWireItem;
import ee.extras.wire.WireComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Отмена выбора без использования блока, расхода предмета или снятия уже проложенного провода. */
public final class WireCancel {
    public record Cancel(boolean offhand) implements CustomPacketPayload {
        static final Type<Cancel> TYPE = new Type<>(EeExtras.rl("cancel_wire_selection"));
        static final StreamCodec<ByteBuf, Cancel> CODEC = ByteBufCodecs.BOOL.map(Cancel::new, Cancel::offhand);
        @Override public Type<Cancel> type() { return TYPE; }
    }
    public static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToServer(
                Cancel.TYPE, Cancel.CODEC, (packet, context) -> cancel(context.player(),
                        packet.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)));
    }
    public static boolean supports(ItemStack stack) {
        return stack.is(Tension.CLAMP_ITEM.get()) || stack.getItem() instanceof SurfaceWireItem
                || stack.getItem() instanceof WireSpoolItem || stack.getItem() instanceof BundledWireItem;
    }
    public static boolean clear(ItemStack stack) {
        if (!supports(stack)) return false;
        boolean selected = stack.has(CEEDataComponents.SELECTED_NODE)
                || stack.has(CEEDataComponents.SELECTED_WIRE_TERMINATION)
                || stack.has(PanelModules.SELECTED_PORT.get()) || stack.has(WireComponents.SELECTION.get());
        stack.remove(CEEDataComponents.SELECTED_NODE);
        stack.remove(CEEDataComponents.SELECTED_WIRE_TERMINATION);
        stack.remove(PanelModules.SELECTED_PORT.get());
        stack.remove(WireComponents.SELECTION.get());
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("ee_extras_anchor_dimension"))
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove("ee_extras_anchor_dimension"));
        return selected;
    }
    public static void cancel(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!clear(stack)) return;
        player.displayClientMessage(Component.translatable(stack.is(Tension.CLAMP_ITEM.get())
                ? "ee_extras.tension.anchor_cancelled" : "electroenergetics.wire_spool.cancelled_connection"), true);
    }
    private WireCancel() {}
}
