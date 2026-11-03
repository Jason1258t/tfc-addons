package ee.extras.thermometer;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import ee.extras.EeExtras;
import ee.extras.Layout;
import ee.extras.PowerBlock;
import ee.extras.Tier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Регистрация только при TFC: класс capability не загружается в минимальном стенде без него. */
public final class Thermometer {
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ThermometerDevice>> DEVICE =
            EeExtras.DEVICES.register("thermometer", () -> new SimulatedDeviceType<ThermometerDevice>(
                    EeExtras.rl("thermometer"), (type, level, pos, devices) -> new ThermometerDevice(level, pos, devices, type)));
    public static final DeferredBlock<PowerBlock<ThermometerDevice>> BLOCK = EeExtras.BLOCKS.register("thermometer",
            () -> new PowerBlock<>(EeExtras.metal(), Layout.COMPACT_GENERATOR, Tier.LIGHT, DEVICE::get));
    public static final DeferredItem<Item> ITEM = EeExtras.ITEMS.register("thermometer",
            () -> new ee.extras.TooltipBlockItem(BLOCK.get(), new Item.Properties()));

    private Thermometer() {}

    public static void init() {}

    public static void addToTab(BuildCreativeModeTabContentsEvent event) {
        event.accept(ITEM.get());
    }
}
