package aero.extra;

import com.george_vi.electroenergetics.CEECreativeTab;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Create Aeronautics Extra — бортовая электрика для машин и кораблей Create Aeronautics (сборка TFC Studio).
 *
 * Корабль Aeronautics — «подуровень» Sable (sable 2.0.5, по байткоду): его блоки лежат в чанках-участках того же
 * ServerLevel (ServerLevelPlot берёт level.getChunkSource()), поэтому блок-сущности, сеть вращения Create и устройства
 * EE (DevicesSavedData по Level + BlockPos) работают на летящем корабле так же, как на земле. Особой поддержки
 * «контрапшенов» блоку не нужно.
 */
@Mod(AeroExtra.MOD_ID)
public class AeroExtra {
    public static final String MOD_ID = "create_aeronautics_extra";

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    /** Заряд ячейки аккумуляторного модуля, Кл */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> CHARGE =
            COMPONENTS.registerComponentType("charge", b -> b.persistent(Codec.DOUBLE).networkSynchronized(ByteBufCodecs.DOUBLE));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<OnboardDevice>> ONBOARD_COMPUTER_DEVICE =
            DEVICES.register("onboard_computer", () -> new SimulatedDeviceType<OnboardDevice>(rl("onboard_computer"),
                    (type, level, pos, devices) -> new OnboardDevice(level, pos, devices, type)));
    public static final DeferredBlock<OnboardComputerBlock> ONBOARD_COMPUTER = BLOCKS.register("onboard_computer",
            () -> new OnboardComputerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(3, 6).sound(SoundType.NETHERITE_BLOCK).noOcclusion()));
    public static final DeferredItem<Item> ONBOARD_COMPUTER_ITEM = ITEMS.register("onboard_computer",
            () -> new BlockItem(ONBOARD_COMPUTER.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.nodes").withStyle(ChatFormatting.DARK_GRAY));
                }
            });
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OnboardComputerBlockEntity>> ONBOARD_COMPUTER_BE =
            BLOCK_ENTITIES.register("onboard_computer", () -> BlockEntityType.Builder
                    .of(OnboardComputerBlockEntity::new, ONBOARD_COMPUTER.get()).build(null));

    public static final DeferredBlock<TransportEngineBlock> TRANSPORT_ENGINE = BLOCKS.register("transport_engine",
            () -> new TransportEngineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3, 6).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> TRANSPORT_ENGINE_ITEM = ITEMS.register("transport_engine",
            () -> new BlockItem(TRANSPORT_ENGINE.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransportEngineBlockEntity>> TRANSPORT_ENGINE_BE =
            BLOCK_ENTITIES.register("transport_engine", () -> BlockEntityType.Builder
                    .of(TransportEngineBlockEntity::new, TRANSPORT_ENGINE.get()).build(null));

    public static final DeferredBlock<FuelPumpBlock> FUEL_PUMP = BLOCKS.register("fuel_pump",
            () -> new FuelPumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3, 6).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> FUEL_PUMP_ITEM = ITEMS.register("fuel_pump",
            () -> new BlockItem(FUEL_PUMP.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelPumpBlockEntity>> FUEL_PUMP_BE =
            BLOCK_ENTITIES.register("fuel_pump", () -> BlockEntityType.Builder
                    .of(FuelPumpBlockEntity::new, FUEL_PUMP.get()).build(null));
    static final DeferredHolder<DataComponentType<?>, DataComponentType<FluidStack>> PUMP_FUEL =
            COMPONENTS.registerComponentType("pump_fuel",
                    b -> b.persistent(FluidStack.CODEC).networkSynchronized(FluidStack.STREAM_CODEC));

    static final DeferredBlock<FuelPortBlock> FUEL_PORT = BLOCKS.register("fuel_port",
            () -> new FuelPortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2, 4).sound(SoundType.METAL).noOcclusion()));
    static final DeferredItem<BlockItem> FUEL_PORT_ITEM = ITEMS.register("fuel_port",
            () -> new BlockItem(FUEL_PORT.get(), new Item.Properties()));

    public static final DeferredItem<ModuleItem> GENERATOR_MODULE = ITEMS.register("generator_module",
            () -> new ModuleItem(ModuleItem.Kind.GENERATOR, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<ModuleItem> BATTERY_MODULE = ITEMS.register("battery_module",
            () -> new ModuleItem(ModuleItem.Kind.BATTERY, new Item.Properties().stacksTo(1)));

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public AeroExtra(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        DEVICES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(AeroExtra::addToTab);
        modBus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FUEL_PUMP_BE.get(), FuelPumpBlockEntity::engineOutlet));
        if (FMLEnvironment.dist == Dist.CLIENT) OnboardClient.register(modBus);
    }

    /** Во вкладку творческого режима Electro Energetics, рядом с остальной электрикой */
    private static void addToTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CEECreativeTab.CREATIVE_TAB.getKey())) return;
        event.accept(ONBOARD_COMPUTER_ITEM.get());
        event.accept(TRANSPORT_ENGINE_ITEM.get());
        event.accept(FUEL_PUMP_ITEM.get());
        event.accept(FUEL_PORT_ITEM.get());
        event.accept(GENERATOR_MODULE.get());
        event.accept(BATTERY_MODULE.get());
    }
}
