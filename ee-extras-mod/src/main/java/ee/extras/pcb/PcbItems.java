package ee.extras.pcb;

import ee.extras.EeExtras;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Платы, проектирование и общие материалы электроники. */
public final class PcbItems {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EeExtras.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, EeExtras.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> PCB_TOPOLOGY =
            DATA_COMPONENTS.register("pcb_topology", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());

    public static final DeferredItem<PcbDesignItem> L1_MASK = EeExtras.ITEMS.register("l1_mask",
            () -> new PcbDesignItem(new Item.Properties().stacksTo(16), 1));
    public static final DeferredItem<Item> L1_BOARD_BLANK = item("l1_board_blank", 16);
    public static final DeferredItem<Item> L1_BOARD = item("l1_board", 16);
    public static final DeferredItem<PcbDesignItem> PCB_DESIGN = EeExtras.ITEMS.register("pcb_design",
            () -> new PcbDesignItem(new Item.Properties().stacksTo(16), 2));

    public static final DeferredBlock<PcbEditorBlock> EDITOR_BLOCK = EeExtras.BLOCKS.register("pcb_design_table",
            () -> new PcbEditorBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> EDITOR_ITEM = EeExtras.ITEMS.register("pcb_design_table",
            () -> new BlockItem(EDITOR_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PcbEditorBlockEntity>> EDITOR_BE =
            EeExtras.BLOCK_ENTITIES.register("pcb_design_table", () -> BlockEntityType.Builder
                    .of(PcbEditorBlockEntity::new, EDITOR_BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<PcbEditorMenu>> EDITOR_MENU = MENUS.register(
            "pcb_design_table", () -> IMenuTypeExtension.create((id, inventory, data) ->
                    new PcbEditorMenu(id, inventory, data.readBlockPos())));

    public static final DeferredItem<Item> COPPER_FOIL = item("copper_foil", 64);
    public static final DeferredItem<Item> GLASS_FIBER = item("glass_fiber", 64);
    public static final DeferredItem<Item> GLASS_FABRIC = item("glass_fabric", 64);
    public static final DeferredItem<Item> EPOXY_LAMINATE = item("epoxy_laminate", 32);
    public static final DeferredItem<Item> L2_BOARD_BLANK = item("l2_board_blank", 16);
    public static final DeferredItem<Item> L3_BOARD_BLANK = item("l3_board_blank", 16);
    public static final DeferredItem<Item> SOLDER_WIRE = item("solder_wire", 64);
    public static final DeferredItem<Item> ROSIN_FLUX = item("rosin_flux", 64);
    public static final DeferredItem<Item> RESISTOR = item("resistor", 64);
    public static final DeferredItem<Item> CAPACITOR = item("capacitor", 64);
    public static final DeferredItem<Item> INDUCTOR = item("inductor", 64);
    public static final DeferredItem<Item> CONNECTOR = item("connector", 64);
    public static final DeferredItem<Item> SILICON_DIODE = item("silicon_diode", 64);
    public static final DeferredItem<Item> TRANSISTOR = item("transistor", 64);
    public static final DeferredItem<Item> BASIC_IC = item("basic_ic", 32);
    public static final DeferredItem<Item> ADVANCED_IC = item("advanced_ic", 16);
    public static final DeferredItem<Item> SILICON_WAFER = item("silicon_wafer", 16);

    private static final List<DeferredItem<? extends Item>> ELECTRONICS = List.of(COPPER_FOIL, GLASS_FIBER, GLASS_FABRIC,
            EPOXY_LAMINATE, L2_BOARD_BLANK, L3_BOARD_BLANK, SOLDER_WIRE, ROSIN_FLUX, RESISTOR, CAPACITOR, INDUCTOR,
            CONNECTOR, SILICON_DIODE, TRANSISTOR, BASIC_IC, ADVANCED_IC, SILICON_WAFER);

    private PcbItems() {}

    private static DeferredItem<Item> item(String id, int stackSize) {
        return EeExtras.ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(stackSize)));
    }

    public static void init() {}

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        MENUS.register(modBus);
    }

    public static void addToTab(BuildCreativeModeTabContentsEvent event) {
        event.accept(EDITOR_ITEM.get());
        event.accept(L1_BOARD_BLANK.get());
        event.accept(L1_MASK.get());
        event.accept(L1_BOARD.get());
        event.accept(PCB_DESIGN.get());
        for (DeferredItem<? extends Item> item : ELECTRONICS) event.accept(item.get());
    }
}
