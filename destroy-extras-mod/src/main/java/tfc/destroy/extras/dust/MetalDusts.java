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
    public record Metal(String id, int tint) {}

    public static final List<Metal> METALS = List.of(
        new Metal("copper", 0xB87333),
        new Metal("iron", 0x8C8C8C),
        new Metal("zinc", 0x8C8C99),
        new Metal("tin", 0xD4D4DA),
        new Metal("bismuth", 0x3F595D),
        new Metal("nickel", 0x676756),
        new Metal("silver", 0xE1D6D6),
        new Metal("gold", 0xD4AF37),
        new Metal("lead", 0x5F5F6A),
        new Metal("aluminum", 0xABB3B6),
        new Metal("chromium", 0xE6E5E2),
        new Metal("uranium", 0x5FA35A),
        new Metal("constantan", 0xC59E83),
        new Metal("electrum", 0xE8D080),
        new Metal("steel", 0x777983),
        new Metal("platinum", 0xD6D6CB),
        new Metal("palladium", 0xCAC9D2),
        new Metal("rhodium", 0xB7BBC1));

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DestroyExtras.MOD_ID);
    private static final List<DeferredItem<Item>> DUSTS = new ArrayList<>();

    static {
        for (Metal metal : METALS) {
            DUSTS.add(ITEMS.register(metal.id() + "_dust", () -> new Item(new Item.Properties())));
        }
    }

    private MetalDusts() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(MetalDusts::creativeTab);
    }

    /** Пыли в порядке {@link #METALS}. */
    public static List<DeferredItem<Item>> dusts() {
        return DUSTS;
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            for (DeferredItem<Item> dust : DUSTS) event.accept(dust.get());
        }
    }
}
