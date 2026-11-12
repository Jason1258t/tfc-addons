package tfc.destroy.extras;

import com.george_vi.electroenergetics.CEECreativeTab;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration;
import tfc.destroy.extras.electrolyzer.ElectrolyzerLogic;
import tfc.destroy.extras.electrolyzer.ElectrolyzerMultiblock;
import tfc.destroy.extras.electrolyzer.ElectrolyzerStructure;
import tfc.destroy.extras.electrolyzer.ExtrasMultiblockBuilder;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlock;
import tfc.destroy.extras.etching.EtchingBathBlock;
import tfc.destroy.extras.dust.MetalDusts;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;
import tfc.destroy.extras.metallurgy.MetallurgicalProcessorBlock;
import tfc.destroy.extras.metallurgy.MetallurgicalProcessorBlockEntity;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;

/** Недостающие машины и процессы Destroy; химия и платы здесь не дублируются. */
@Mod(DestroyExtras.MOD_ID)
public final class DestroyExtras {
    public static final String MOD_ID = "destroy_extras";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<MembraneElectrolyzerBlock> ELECTROLYZER_BLOCK = BLOCKS.register(
        "membrane_electrolyzer", () -> new MembraneElectrolyzerBlock(BlockBehaviour.Properties.of()
            .strength(3.0f).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> ELECTROLYZER_ITEM = ITEMS.register("membrane_electrolyzer",
        () -> new BlockItem(ELECTROLYZER_BLOCK.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                tooltip.add(Component.translatable("tooltip.destroy_extras.electrolyzer.power"));
                tooltip.add(Component.translatable("tooltip.destroy_extras.electrolyzer.outputs"));
                tooltip.add(Component.translatable("tooltip.destroy_extras.electrolyzer.structure"));
                tooltip.add(Component.translatable("tooltip.destroy_extras.electrolyzer.membrane"));
            }
        });
    public static final DeferredItem<Item> MEMBRANE_ITEM = ITEMS.register("membrane",
        () -> new Item(new Item.Properties().durability(ElectrolyzerLogic.MEMBRANE_OPERATIONS)));
    /** Собранный электролизёр: блок, блок-сущности и регистрация мультиблока IE (порядок полей важен — регистры выше). */
    public static final MultiblockRegistration<ElectrolyzerLogic.State> ELECTROLYZER = ElectrolyzerLogic.REGISTRATION;
    public static ElectrolyzerMultiblock ELECTROLYZER_MULTIBLOCK;
    public static final DeferredBlock<EtchingBathBlock> ETCHING_BATH_BLOCK = BLOCKS.register("etching_bath",
        () -> new EtchingBathBlock(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> ETCHING_BATH_ITEM = ITEMS.register("etching_bath",
        () -> new BlockItem(ETCHING_BATH_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EtchingBathBlockEntity>> ETCHING_BATH_BE =
        BLOCK_ENTITIES.register("etching_bath", () -> BlockEntityType.Builder
            .of(EtchingBathBlockEntity::new, ETCHING_BATH_BLOCK.get()).build(null));
    public static final DeferredBlock<MetallurgicalProcessorBlock> METALLURGY_BLOCK = BLOCKS.register("metallurgical_processor",
        () -> new MetallurgicalProcessorBlock(BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredItem<BlockItem> METALLURGY_ITEM = ITEMS.register("metallurgical_processor",
        () -> new BlockItem(METALLURGY_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetallurgicalProcessorBlockEntity>> METALLURGY_BE =
        BLOCK_ENTITIES.register("metallurgical_processor", () -> BlockEntityType.Builder
            .of(MetallurgicalProcessorBlockEntity::new, METALLURGY_BLOCK.get()).build(null));

    public static final DeferredItem<Item> ROASTED_TETRAHEDRITE = intermediate("roasted_tetrahedrite");
    public static final DeferredItem<Item> ROASTED_SPHALERITE = intermediate("roasted_sphalerite");
    public static final DeferredItem<Item> GANGUE = intermediate("gangue");
    public static final DeferredItem<Item> IRON_RESIDUE = intermediate("iron_residue");
    public static final DeferredItem<Item> RED_MUD = intermediate("red_mud");
    public static final DeferredItem<Item> ALUMINUM_HYDROXIDE = intermediate("aluminum_hydroxide");
    public static final DeferredItem<Item> ALUMINA = intermediate("alumina");
    public static final DeferredItem<Item> CEMENT_COPPER = intermediate("cement_copper");
    public static final DeferredItem<Item> NICKEL_SULFIDE = intermediate("nickel_sulfide");
    public static final DeferredItem<Item> ELECTRONIC_SCRAP = intermediate("electronic_scrap");
    public static final DeferredItem<Item> PRECIOUS_RESIDUE = intermediate("precious_residue");
    public static final DeferredItem<Item> STABILIZED_TAILINGS = intermediate("stabilized_tailings");

    private static DeferredItem<Item> intermediate(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties()));
    }

    public DestroyExtras(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ExtrasRecipeTypes.register(modBus);
        MetalDusts.register(modBus);
        ELECTROLYZER_MULTIBLOCK = new ElectrolyzerMultiblock();
        MultiblockHandler.registerMultiblock(ELECTROLYZER_MULTIBLOCK);
        ExtrasMultiblockBuilder.registerOnModBus(modBus);
        modBus.addListener(DestroyExtras::capabilities);
        modBus.addListener(DestroyExtras::creativeTab);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ETCHING_BATH_BE.get(), (be, side) -> {
            Direction facing = be.getBlockState().getValue(EtchingBathBlock.FACING);
            Direction local = ElectrolyzerStructure.toLocal(facing, side);
            if (local == Direction.NORTH) return be.spent;
            if (local == Direction.SOUTH) return be.hydrochloricAcid;
            if (local == Direction.EAST) return be.water;
            return be.input;
        });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ETCHING_BATH_BE.get(), (be, side) -> be.items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, METALLURGY_BE.get(), (be, side) -> {
            if (side == Direction.NORTH) return be.reagent;
            if (side == Direction.EAST) return be.output;
            if (side == Direction.SOUTH) return be.secondaryOutput;
            return be.input;
        });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, METALLURGY_BE.get(), (be, side) -> be.items);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CEECreativeTab.CREATIVE_TAB)) {
            event.accept(ELECTROLYZER_ITEM.get());
            event.accept(MEMBRANE_ITEM.get());
            event.accept(ETCHING_BATH_ITEM.get());
            event.accept(METALLURGY_ITEM.get());
            event.accept(ROASTED_TETRAHEDRITE.get());
            event.accept(ROASTED_SPHALERITE.get());
            event.accept(GANGUE.get());
            event.accept(IRON_RESIDUE.get());
            event.accept(RED_MUD.get());
            event.accept(ALUMINUM_HYDROXIDE.get());
            event.accept(ALUMINA.get());
            event.accept(CEMENT_COPPER.get());
            event.accept(NICKEL_SULFIDE.get());
            event.accept(ELECTRONIC_SCRAP.get());
            event.accept(PRECIOUS_RESIDUE.get());
            event.accept(STABILIZED_TAILINGS.get());
        }
    }
}
