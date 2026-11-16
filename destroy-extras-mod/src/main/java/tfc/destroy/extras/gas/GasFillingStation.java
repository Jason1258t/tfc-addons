package tfc.destroy.extras.gas;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tfc.destroy.extras.DestroyExtras;

public final class GasFillingStation {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DestroyExtras.MOD_ID);
    static final DeferredHolder<DataComponentType<?>, DataComponentType<GasCylinderItem.Contents>> GAS =
        COMPONENTS.register("cylinder_gas", () -> DataComponentType.<GasCylinderItem.Contents>builder()
            .persistent(GasCylinderItem.Contents.CODEC).build());

    public static final DeferredBlock<GasFillingStationBlock> BLOCK = DestroyExtras.BLOCKS.register("gas_filling_station",
        () -> new GasFillingStationBlock(BlockBehaviour.Properties.of().strength(3).requiresCorrectToolForDrops()));
    static final DeferredItem<BlockItem> ITEM = DestroyExtras.ITEMS.register("gas_filling_station",
        () -> new BlockItem(BLOCK.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("tooltip.destroy_extras.gas_station.power"));
                tooltip.add(Component.translatable("tooltip.destroy_extras.gas_station.controls"));
            }
        });
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasFillingStationEntity>> ENTITY =
        DestroyExtras.BLOCK_ENTITIES.register("gas_filling_station", () -> BlockEntityType.Builder
            .of(GasFillingStationEntity::new, BLOCK.get()).build(null));
    static final DeferredBlock<GasCylinderBlock> STEEL_BLOCK = cylinderBlock("gas_cylinder_steel");
    static final DeferredBlock<GasCylinderBlock> STAINLESS_BLOCK = cylinderBlock("gas_cylinder_stainless_steel");
    static final DeferredBlock<GasCylinderBlock> BLUE_STEEL_BLOCK = cylinderBlock("gas_cylinder_blue_steel");
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasCylinderEntity>> CYLINDER_ENTITY =
        DestroyExtras.BLOCK_ENTITIES.register("gas_cylinder", () -> BlockEntityType.Builder
            .of(GasCylinderEntity::new, STEEL_BLOCK.get(), STAINLESS_BLOCK.get(), BLUE_STEEL_BLOCK.get()).build(null));
    public static final DeferredItem<GasCylinderItem> STEEL = cylinder(STEEL_BLOCK, 10, 2);
    public static final DeferredItem<GasCylinderItem> STAINLESS = cylinder(STAINLESS_BLOCK, 20, 4);
    public static final DeferredItem<GasCylinderItem> BLUE_STEEL = cylinder(BLUE_STEEL_BLOCK, 40, 6);

    private GasFillingStation() {}

    private static DeferredBlock<GasCylinderBlock> cylinderBlock(String id) {
        return DestroyExtras.BLOCKS.register(id, () -> new GasCylinderBlock(BlockBehaviour.Properties.of()
            .strength(2.5f).requiresCorrectToolForDrops().noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    }

    private static DeferredItem<GasCylinderItem> cylinder(DeferredBlock<GasCylinderBlock> block, int volume, int pressure) {
        return DestroyExtras.ITEMS.register(block.getId().getPath(), () -> new GasCylinderItem(block.get(), volume, pressure));
    }

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        bus.addListener(GasFillingStation::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ENTITY.get(),
            (station, side) -> station.portSide() == side ? station.inlet : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CYLINDER_ENTITY.get(),
            (cylinder, side) -> side == net.minecraft.core.Direction.UP ? cylinder.port() : null);
    }

    public static void addToTab(BuildCreativeModeTabContentsEvent event) {
        event.accept(ITEM.get());
        event.accept(STEEL.get());
        event.accept(STAINLESS.get());
        event.accept(BLUE_STEEL.get());
    }
}
