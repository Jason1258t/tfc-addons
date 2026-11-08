package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
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
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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

    private static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, EeExtras.MOD_ID);
    private static final DeferredHolder<DisplaySource, PcbMeterSource> METER_SOURCE =
            DISPLAY_SOURCES.register("pcb_meter", PcbMeterSource::new);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> PCB_TOPOLOGY =
            DATA_COMPONENTS.register("pcb_topology", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());

    public static final DeferredItem<PcbDesignItem> L1_MASK = EeExtras.ITEMS.register("l1_mask",
            () -> new PcbDesignItem(new Item.Properties().stacksTo(16), 1));
    public static final DeferredItem<Item> L1_BOARD_BLANK = item("l1_board_blank", 16);
    public static final DeferredItem<Item> L1_BOARD = EeExtras.ITEMS.register("l1_board", () -> new PcbBoardItem(1));
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

    public static final DeferredHolder<com.george_vi.electroenergetics.devices.device.SimulatedDeviceType<?>,
            com.george_vi.electroenergetics.devices.device.SimulatedDeviceType<PcbMachineDevice>> PCB_MACHINE_DEVICE =
            EeExtras.DEVICES.register("pcb_machine", () -> new com.george_vi.electroenergetics.devices.device.SimulatedDeviceType<>(
                    EeExtras.rl("pcb_machine"), (type, level, pos, devices) -> new PcbMachineDevice(level, pos, devices, type)));
    public static final DeferredBlock<PcbMachineBlock> PHOTOLITHOGRAPHER_BLOCK = EeExtras.BLOCKS.register(
            "pcb_photolithographer", () -> new PcbMachineBlock(EeExtras.metal(), PcbMachineKind.PHOTOLITHOGRAPHY));
    public static final DeferredItem<BlockItem> PHOTOLITHOGRAPHER_ITEM = EeExtras.ITEMS.register(
            "pcb_photolithographer", () -> new BlockItem(PHOTOLITHOGRAPHER_BLOCK.get(), new Item.Properties()));
    public static final DeferredBlock<PcbMachineBlock> METALLIZER_BLOCK = EeExtras.BLOCKS.register(
            "pcb_via_metallizer", () -> new PcbMachineBlock(EeExtras.metal(), PcbMachineKind.METALLIZATION));
    public static final DeferredItem<BlockItem> METALLIZER_ITEM = EeExtras.ITEMS.register(
            "pcb_via_metallizer", () -> new BlockItem(METALLIZER_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PcbMachineBlockEntity>> PCB_MACHINE_BE =
            EeExtras.BLOCK_ENTITIES.register("pcb_machine", () -> BlockEntityType.Builder
                    .of(PcbMachineBlockEntity::new, PHOTOLITHOGRAPHER_BLOCK.get(), METALLIZER_BLOCK.get()).build(null));

    public static final DeferredItem<Item> COPPER_FOIL = item("copper_foil", 64);
    public static final DeferredItem<Item> GLASS_FIBER = item("glass_fiber", 64);
    public static final DeferredItem<Item> GLASS_FABRIC = item("glass_fabric", 64);
    public static final DeferredItem<Item> EPOXY_LAMINATE = item("epoxy_laminate", 32);
    public static final DeferredItem<Item> PHOTORESIST = item("photoresist", 64);
    public static final DeferredItem<Item> L2_BOARD_BLANK = item("l2_board_blank", 16);
    public static final DeferredItem<Item> L3_BOARD_BLANK = item("l3_board_blank", 16);
    public static final DeferredItem<Item> L2_PREPARED_BLANK = item("l2_prepared_blank", 16);
    public static final DeferredItem<Item> L3_PREPARED_BLANK = item("l3_prepared_blank", 16);
    public static final DeferredItem<Item> L2_ETCHED_BOARD = item("l2_etched_board", 16);
    public static final DeferredItem<Item> L3_ETCHED_BOARD = item("l3_etched_board", 16);
    public static final DeferredItem<Item> L2_BOARD = EeExtras.ITEMS.register("l2_board", () -> new PcbBoardItem(2));
    public static final DeferredItem<Item> L3_BOARD = EeExtras.ITEMS.register("l3_board", () -> new PcbBoardItem(3));
    public static final DeferredItem<Item> SOLDER_WIRE = item("solder_wire", 64);
    public static final DeferredItem<Item> ROSIN_FLUX = item("rosin_flux", 64);
    public static final DeferredItem<Item> BUTTON_CAP = item("pcb_button_cap", 64);
    public static final DeferredItem<Item> SWITCH_CAP = item("pcb_switch_cap", 64);
    public static final DeferredItem<Item> LED = item("pcb_led", 64);
    public static final DeferredItem<Item> RESISTOR = item("resistor", 64);
    public static final DeferredItem<Item> CAPACITOR = item("capacitor", 64);
    public static final DeferredItem<Item> INDUCTOR = item("inductor", 64);
    public static final DeferredItem<Item> CONNECTOR = item("connector", 64);
    public static final DeferredItem<Item> SILICON_DIODE = item("silicon_diode", 64);
    public static final DeferredItem<Item> TRANSISTOR = item("transistor", 64);
    public static final DeferredItem<Item> BASIC_IC = item("basic_ic", 32);
    public static final DeferredItem<Item> ADVANCED_IC = item("advanced_ic", 16);
    public static final DeferredItem<Item> SILICON_WAFER = item("silicon_wafer", 16);
    public static final DeferredItem<Item> ABS_SHEET = item("abs_sheet", 64);
    public static final DeferredItem<Item> ABS_KEYBOARD_BLANK = item("abs_keyboard_blank", 32);
    public static final DeferredItem<Item> PET_FILM = item("pet_film", 64);
    public static final DeferredItem<Item> RGB_PHOSPHOR = item("rgb_phosphor", 64);
    public static final DeferredItem<Item> SHADOW_MASK = item("shadow_mask", 32);
    public static final DeferredItem<Item> PERMANENT_MAGNET = item("permanent_magnet", 32);
    public static final DeferredItem<Item> SPEAKER_CONE = item("speaker_cone", 32);
    public static final DeferredItem<Item> BATTERY_ELECTROLYTE = item("battery_electrolyte", 32);
    public static final DeferredItem<Item> INK_CARTRIDGE = item("ink_cartridge", 32);

    // Функциональные модули компьютеров и периферии CC. Это обычные предметы: поведение и данные устройств
    // остаются у CC, а новые идентификаторы нужны только для физической производственной цепочки.
    public static final DeferredItem<Item> CONTROL_BOARD_L1 = item("control_board_l1", 16);
    public static final DeferredItem<Item> COMPUTER_MOTHERBOARD = item("computer_motherboard", 16);
    public static final DeferredItem<Item> ADVANCED_MOTHERBOARD = item("advanced_motherboard", 16);
    public static final DeferredItem<Item> TURTLE_CONTROLLER = item("turtle_controller", 16);
    public static final DeferredItem<Item> ADVANCED_TURTLE_CONTROLLER = item("advanced_turtle_controller", 16);
    public static final DeferredItem<Item> KEYBOARD = item("keyboard", 16);
    public static final DeferredItem<Item> POCKET_INPUT = item("pocket_input", 16);
    public static final DeferredItem<Item> DISPLAY_DRIVER = item("display_driver", 16);
    public static final DeferredItem<Item> ADVANCED_DISPLAY_DRIVER = item("advanced_display_driver", 16);
    public static final DeferredItem<Item> DISPLAY_CRT = item("display_crt", 16);
    public static final DeferredItem<Item> DISPLAY_ADVANCED = item("display_advanced", 16);
    public static final DeferredItem<Item> ANTENNA = item("antenna", 32);
    public static final DeferredItem<Item> POCKET_COLOR_DISPLAY = item("pocket_color_display", 16);
    public static final DeferredItem<Item> POCKET_DISPLAY = item("pocket_display", 16);
    public static final DeferredItem<Item> NETWORK_PORT = item("network_port", 32);
    public static final DeferredItem<Item> COMPUTER_CASE = item("computer_case", 8);
    public static final DeferredItem<Item> ADVANCED_COMPUTER_CASE = item("advanced_computer_case", 8);
    public static final DeferredItem<Item> POCKET_CASE = item("pocket_case", 16);
    public static final DeferredItem<Item> PERIPHERAL_CASE = item("peripheral_case", 16);
    public static final DeferredItem<Item> SMALL_MOTOR = item("small_motor", 16);
    public static final DeferredItem<Item> PRECISION_ACTUATOR = item("precision_actuator", 16);
    public static final DeferredItem<Item> STORAGE_BAY = item("storage_bay", 8);
    public static final DeferredItem<Item> ROBOT_CHASSIS = item("robot_chassis", 4);
    public static final DeferredItem<Item> DISK_TRANSPORT = item("disk_transport", 8);
    public static final DeferredItem<Item> PRINT_MECHANISM = item("print_mechanism", 8);
    public static final DeferredItem<Item> NETWORK_TRANSCEIVER = item("network_transceiver", 16);
    public static final DeferredItem<Item> ENDER_TRANSCEIVER = item("ender_transceiver", 16);
    public static final DeferredItem<Item> SPEAKER_DRIVER = item("speaker_driver", 16);
    public static final DeferredItem<Item> AUDIO_AMPLIFIER = item("audio_amplifier", 16);
    public static final DeferredItem<Item> LED_PANEL = item("pcb_led_panel", 16);
    public static final DeferredItem<Item> LED_STRIP = item("pcb_led_strip", 16);
    public static final DeferredItem<Item> D_FLIP_FLOP = item("pcb_flip_flop", 16);
public static final DeferredItem<Item> COUNTER = item("pcb_counter", 16);
public static final DeferredItem<Item> REGISTER = item("pcb_register", 16);
public static final DeferredItem<Item> DECODER = item("pcb_decoder", 16);
    public static final DeferredItem<Item> COMPARATOR = item("pcb_comparator", 16);
    public static final DeferredItem<Item> SELECTOR = item("pcb_selector", 16);
    public static final DeferredItem<Item> LATCHING_RELAY = item("pcb_latching_relay", 16);
    public static final DeferredItem<Item> MECHANICAL_TIMER = item("pcb_mechanical_timer", 16);
    public static final DeferredItem<Item> CLOCK = item("pcb_clock", 16);
    public static final DeferredItem<Item> RHEOSTAT = item("pcb_rheostat", 16);
    public static final DeferredItem<Item> POTENTIOMETER = item("pcb_potentiometer", 16);
    public static final DeferredItem<Item> STEP_UP_TRANSFORMER = item("pcb_step_up_transformer", 16);
    public static final DeferredItem<Item> ONBOARD_TRANSFORMER = item("pcb_onboard_transformer", 16);
    public static final DeferredItem<Item> POWER_SUPPLY = item("power_supply", 16);
    public static final DeferredItem<Item> BATTERY_MODULE = item("battery_module", 16);
    public static final DeferredItem<Item> MAGNETIC_DISK_MEDIUM = item("magnetic_disk_medium", 32);

    private static final List<DeferredItem<? extends Item>> ELECTRONICS = List.of(COPPER_FOIL, GLASS_FIBER, GLASS_FABRIC,
            EPOXY_LAMINATE, PHOTORESIST, L2_BOARD_BLANK, L3_BOARD_BLANK, L2_PREPARED_BLANK, L3_PREPARED_BLANK,
            L2_ETCHED_BOARD, L3_ETCHED_BOARD, L2_BOARD, L3_BOARD, SOLDER_WIRE, ROSIN_FLUX, LED, BUTTON_CAP, SWITCH_CAP, RHEOSTAT, POTENTIOMETER, MECHANICAL_TIMER, CLOCK, LED_PANEL, LED_STRIP, LATCHING_RELAY, SELECTOR, COMPARATOR, D_FLIP_FLOP, COUNTER, REGISTER, DECODER, RESISTOR, CAPACITOR, INDUCTOR,
            CONNECTOR, SILICON_DIODE, TRANSISTOR, BASIC_IC, ADVANCED_IC, SILICON_WAFER, ABS_SHEET, ABS_KEYBOARD_BLANK,
            PET_FILM, RGB_PHOSPHOR, SHADOW_MASK, PERMANENT_MAGNET, SPEAKER_CONE, BATTERY_ELECTROLYTE, INK_CARTRIDGE,
            CONTROL_BOARD_L1,
            COMPUTER_MOTHERBOARD, ADVANCED_MOTHERBOARD, TURTLE_CONTROLLER, ADVANCED_TURTLE_CONTROLLER, KEYBOARD,
            POCKET_INPUT, DISPLAY_DRIVER, ADVANCED_DISPLAY_DRIVER, DISPLAY_CRT, DISPLAY_ADVANCED, POCKET_DISPLAY, POCKET_COLOR_DISPLAY, ANTENNA,
            NETWORK_PORT, COMPUTER_CASE, ADVANCED_COMPUTER_CASE, POCKET_CASE, PERIPHERAL_CASE, SMALL_MOTOR,
            PRECISION_ACTUATOR, STORAGE_BAY, ROBOT_CHASSIS, DISK_TRANSPORT, PRINT_MECHANISM, NETWORK_TRANSCEIVER,
            ENDER_TRANSCEIVER, SPEAKER_DRIVER, AUDIO_AMPLIFIER, STEP_UP_TRANSFORMER, ONBOARD_TRANSFORMER, POWER_SUPPLY, BATTERY_MODULE, MAGNETIC_DISK_MEDIUM);

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<PcbCircuitDevice>> CIRCUIT_DEVICE =
            EeExtras.DEVICES.register("pcb_circuit", () -> new SimulatedDeviceType<>(
                    EeExtras.rl("pcb_circuit"), (type, level, pos, devices) -> new PcbCircuitDevice(level, pos, devices, type)));
    public static final DeferredBlock<PcbCircuitBlock> CIRCUIT_BLOCK = EeExtras.BLOCKS.register("pcb_circuit",
            () -> new PcbCircuitBlock(EeExtras.metal().noOcclusion().lightLevel(state -> state.getValue(PcbCircuitBlock.LIT) ? 7 : 0)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PcbCircuitBlockEntity>> CIRCUIT_BE =
            EeExtras.BLOCK_ENTITIES.register("pcb_circuit", () -> BlockEntityType.Builder
                    .of(PcbCircuitBlockEntity::new, CIRCUIT_BLOCK.get()).build(null));

    static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice>>
            SUPPLY_DEVICE = EeExtras.DEVICES.register("pcb_supply", () -> new SimulatedDeviceType<>(
                    EeExtras.rl("pcb_supply"), (type, level, pos, devices) -> new PcbSupplyDevice(level, pos, devices, type)));
    static final DeferredBlock<PcbSupplyBlock> SUPPLY = EeExtras.BLOCKS.register("pcb_supply",
            () -> new PcbSupplyBlock(EeExtras.metal().noOcclusion(), false));
    static final DeferredBlock<PcbSupplyBlock> SUPPLY_DC = EeExtras.BLOCKS.register("pcb_supply_dc",
            () -> new PcbSupplyBlock(EeExtras.metal().noOcclusion(), true));
    static final DeferredItem<PcbSupplyItem> SUPPLY_ITEM = EeExtras.ITEMS.register("pcb_supply",
            () -> new PcbSupplyItem(SUPPLY.get()));
    static final DeferredItem<PcbSupplyItem> SUPPLY_DC_ITEM = EeExtras.ITEMS.register("pcb_supply_dc",
            () -> new PcbSupplyItem(SUPPLY_DC.get()));

    public static final DeferredItem<Item> BURNT_BOARD = item("burnt_board", 16);

    private PcbItems() {}

    private static DeferredItem<Item> item(String id, int stackSize) {
        return EeExtras.ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(stackSize)));
    }

    public static void init() {}

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        DISPLAY_SOURCES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
                () -> DisplaySource.BY_BLOCK.add(CIRCUIT_BLOCK.get(), METER_SOURCE.get())));
        MENUS.register(modBus);
        PcbProcessRecipes.register(modBus);
    }

    public static void addToTab(BuildCreativeModeTabContentsEvent event) {
        event.accept(EDITOR_ITEM.get());
        event.accept(SUPPLY_ITEM.get());
        event.accept(SUPPLY_DC_ITEM.get());
        event.accept(PHOTOLITHOGRAPHER_ITEM.get());
        event.accept(METALLIZER_ITEM.get());
        event.accept(L1_BOARD_BLANK.get());
        event.accept(L1_MASK.get());
        event.accept(L1_BOARD.get());
        event.accept(PCB_DESIGN.get());
        for (DeferredItem<? extends Item> item : ELECTRONICS) event.accept(item.get());
    }
}
