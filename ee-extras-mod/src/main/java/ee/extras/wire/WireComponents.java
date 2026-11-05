package ee.extras.wire;

import ee.extras.EeExtras;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компонент предмета провода: выбранный конец (WireEnd.save), как CONNECTION_DATA у Power Grid */
public final class WireComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EeExtras.MOD_ID);
    public static final Supplier<DataComponentType<CustomData>> SELECTION = COMPONENTS.registerComponentType("wire_selection",
            b -> b.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    private WireComponents() {
    }
}
