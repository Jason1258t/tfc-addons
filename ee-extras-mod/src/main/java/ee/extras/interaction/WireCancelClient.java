package ee.extras.interaction;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class WireCancelClient {
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, WireCancelClient::use);
        bus.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) -> {
            if (WireCancel.supports(event.getItemStack())) event.getToolTip().add(
                    net.minecraft.network.chat.Component.translatable("ee_extras.wire.cancel_hint")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        });
    }
    private static void use(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || mc.screen != null) return;
        long window = mc.getWindow().getWindow();
        boolean control = Screen.hasControlDown()
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (!control || !WireCancel.supports(mc.player.getItemInHand(event.getHand()))) return;
        // Не даём обычному клику выбрать другую гирлянду, поставить зажим или продолжить провод.
        event.setCanceled(true);
        event.setSwingHand(false);
        WireCancel.clear(mc.player.getItemInHand(event.getHand()));
        PacketDistributor.sendToServer(new WireCancel.Cancel(event.getHand() == InteractionHand.OFF_HAND));
    }
    private WireCancelClient() {}
}
