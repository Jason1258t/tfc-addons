package tfc.destroy.extras.dust;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tfc.destroy.extras.DestroyExtras;

/** Чистые пыли металлов: одна пыль на металл, основа единой системы пылей (dev-задача #212). */
public final class MetalDusts {
    private static final List<String> METALS = List.of(
        "copper",
        "iron",
        "zinc",
        "tin",
        "bismuth",
        "nickel",
        "silver",
        "gold",
        "lead",
        "aluminum",
        "chromium",
        "uranium",
        "constantan",
        "electrum",
        "steel",
        "platinum",
        "palladium",
        "rhodium");

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DestroyExtras.MOD_ID);
    private static final List<DeferredItem<Item>> DUSTS = new ArrayList<>();

    static {
        for (String metal : METALS) {
            DUSTS.add(ITEMS.register(metal + "_dust", () -> new Item(new Item.Properties())));
        }
    }

    private MetalDusts() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(MetalDusts::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            for (DeferredItem<Item> dust : DUSTS) event.accept(dust.get());
        }
    }
}
