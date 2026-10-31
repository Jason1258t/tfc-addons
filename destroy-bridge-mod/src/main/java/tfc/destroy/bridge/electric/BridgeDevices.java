package tfc.destroy.bridge.electric;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tfc.destroy.bridge.DestroyBridge;

/** Регистрация общего EE-драйвера моста; игровых блоков здесь нет. */
public final class BridgeDevices {
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
        DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, DestroyBridge.MOD_ID);

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ProcessPowerDevice>> PROCESS_POWER =
        DEVICES.register("process_power", () -> new SimulatedDeviceType<>(
            ResourceLocation.fromNamespaceAndPath(DestroyBridge.MOD_ID, "process_power"),
            (type, level, pos, devices) -> new ProcessPowerDevice(level, pos, devices, type)));

    private BridgeDevices() {}

    public static void register(IEventBus modBus) {
        DEVICES.register(modBus);
    }
}
