package tfc.destroy.bridge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import tfc.destroy.bridge.electric.BridgeDevices;

/** Интеграция Destroy с TFC, Create и Electro Energetics. */
@Mod(DestroyBridge.MOD_ID)
public final class DestroyBridge {
    public static final String MOD_ID = "destroy_bridge";

    public DestroyBridge(IEventBus modBus) {
        BridgeDevices.register(modBus);
    }
}
