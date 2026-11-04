package tfc.destroy.extras.dust;

import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import tfc.destroy.extras.DestroyExtras;

@EventBusSubscriber(modid = DestroyExtras.MOD_ID, value = Dist.CLIENT)
public final class MetalDustsClient {
    private MetalDustsClient() {}

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        for (int i = 0; i < MetalDusts.METALS.size(); i++) {
            int tint = MetalDusts.METALS.get(i).tint();
            DeferredItem<Item> dust = MetalDusts.dusts().get(i);
            event.register((stack, tintIndex) -> tintIndex == 0 ? tint : -1, dust.get());
        }
    }
}
