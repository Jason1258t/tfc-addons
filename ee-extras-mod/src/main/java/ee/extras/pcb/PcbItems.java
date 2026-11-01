package ee.extras.pcb;

import ee.extras.EeExtras;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Физические предметы L1; редактор и правила проверки проекта добавляются отдельным этапом. */
public final class PcbItems {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EeExtras.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> PCB_TOPOLOGY =
            DATA_COMPONENTS.register("pcb_topology", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());

    public static final DeferredItem<Item> L1_BOARD_BLANK = EeExtras.ITEMS.register("l1_board_blank",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> L1_MASK = EeExtras.ITEMS.register("l1_mask",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> L1_BOARD = EeExtras.ITEMS.register("l1_board",
            () -> new Item(new Item.Properties().stacksTo(16)));

    private PcbItems() {}

    public static void init() {}

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }

    public static void addToTab(BuildCreativeModeTabContentsEvent event) {
        event.accept(L1_BOARD_BLANK.get());
        event.accept(L1_MASK.get());
        event.accept(L1_BOARD.get());
    }
}
