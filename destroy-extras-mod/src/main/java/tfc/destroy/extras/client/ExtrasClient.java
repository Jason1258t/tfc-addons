package tfc.destroy.extras.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import tfc.destroy.extras.DestroyExtras;

/** Клиентская регистрация: экраны машин. На выделенном сервере класс не загружается. */
@EventBusSubscriber(modid = DestroyExtras.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExtrasClient {
    private ExtrasClient() {}

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(DestroyExtras.ELECTROLYZER_MENU.get(), ElectrolyzerScreen::new);
    }
}
