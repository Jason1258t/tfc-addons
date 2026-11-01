package tfc.destroy.extras;

import com.george_vi.electroenergetics.CEECreativeTab;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlock;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlockEntity;
import tfc.destroy.extras.etching.EtchingBathBlock;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;

/** Недостающие машины и процессы Destroy; химия и платы здесь не дублируются. */
@Mod(DestroyExtras.MOD_ID)
public final class DestroyExtras {
    public static final String MOD_ID = "destroy_extras";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<MembraneElectrolyzerBlock> ELECTROLYZER_BLOCK = BLOCKS.register(
        "membrane_electrolyzer", () -> new MembraneElectrolyzerBlock(BlockBehaviour.Properties.of()
            .strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> ELECTROLYZER_ITEM = ITEMS.register("membrane_electrolyzer",
        () -> new BlockItem(ELECTROLYZER_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<Item> MEMBRANE_ITEM = ITEMS.register("membrane",
        () -> new Item(new Item.Properties().durability(MembraneElectrolyzerBlockEntity.MEMBRANE_OPERATIONS)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MembraneElectrolyzerBlockEntity>> ELECTROLYZER_BE =
        BLOCK_ENTITIES.register("membrane_electrolyzer", () -> BlockEntityType.Builder
            .of(MembraneElectrolyzerBlockEntity::new, ELECTROLYZER_BLOCK.get()).build(null));
    public static final DeferredBlock<EtchingBathBlock> ETCHING_BATH_BLOCK = BLOCKS.register("etching_bath",
        () -> new EtchingBathBlock(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> ETCHING_BATH_ITEM = ITEMS.register("etching_bath",
        () -> new BlockItem(ETCHING_BATH_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EtchingBathBlockEntity>> ETCHING_BATH_BE =
        BLOCK_ENTITIES.register("etching_bath", () -> BlockEntityType.Builder
            .of(EtchingBathBlockEntity::new, ETCHING_BATH_BLOCK.get()).build(null));

    public DestroyExtras(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ExtrasRecipeTypes.register(modBus);
        modBus.addListener(DestroyExtras::capabilities);
        modBus.addListener(DestroyExtras::creativeTab);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ELECTROLYZER_BE.get(), (be, side) -> {
            if (side == Direction.NORTH) return be.hydrogen;
            if (side == Direction.SOUTH) return be.chlorine;
            if (side == Direction.EAST) return be.sodiumHydroxide;
            return be.input;
        });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ELECTROLYZER_BE.get(), (be, side) -> be.membrane);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ETCHING_BATH_BE.get(),
            (be, side) -> side == Direction.NORTH ? be.spent : be.input);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ETCHING_BATH_BE.get(), (be, side) -> be.items);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CEECreativeTab.CREATIVE_TAB)) {
            event.accept(ELECTROLYZER_ITEM.get());
            event.accept(MEMBRANE_ITEM.get());
            event.accept(ETCHING_BATH_ITEM.get());
        }
    }
}
