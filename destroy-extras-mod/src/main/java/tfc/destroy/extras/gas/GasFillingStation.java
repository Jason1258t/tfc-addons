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
    public static final DeferredItem<GasCylinderItem> STEEL = cylinder("gas_cylinder_steel", 10, 2);
    public static final DeferredItem<GasCylinderItem> STAINLESS = cylinder("gas_cylinder_stainless_steel", 20, 4);
    public static final DeferredItem<GasCylinderItem> BLUE_STEEL = cylinder("gas_cylinder_blue_steel", 40, 6);

    private GasFillingStation() {}

    private static DeferredItem<GasCylinderItem> cylinder(String id, int volume, int pressure) {
        return DestroyExtras.ITEMS.register(id, () -> new GasCylinderItem(volume, pressure));
    }

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        bus.addListener(GasFillingStation::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ENTITY.get(),
            (station, side) -> station.portSide() == side ? station.inlet : null);
    }

    public static void addToTab(BuildCreativeModeTabContentsEvent event) {
        event.accept(ITEM.get());
        event.accept(STEEL.get());
        event.accept(STAINLESS.get());
        event.accept(BLUE_STEEL.get());
    }
}
