package tfc.destroy.extras.gas;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import tfc.destroy.extras.DestroyExtras;

public final class GasPipes {
    public static final DeferredBlock<GasPipeBlock> HYDROGEN = pipe("hydrogen_pipe");
    public static final DeferredBlock<GasPipeBlock> CHLORINE = pipe("chlorine_pipe");
    public static final DeferredItem<BlockItem> HYDROGEN_ITEM = DestroyExtras.ITEMS.registerSimpleBlockItem(HYDROGEN);
    public static final DeferredItem<BlockItem> CHLORINE_ITEM = DestroyExtras.ITEMS.registerSimpleBlockItem(CHLORINE);

    private GasPipes() {}

    private static DeferredBlock<GasPipeBlock> pipe(String id) {
        return DestroyExtras.BLOCKS.register(id,
            () -> new GasPipeBlock(BlockBehaviour.Properties.of().strength(1.5f).requiresCorrectToolForDrops().noOcclusion()));
    }

    public static void register(IEventBus bus) {
        bus.addListener(GasPipes::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (GasPipeBlock pipe : new GasPipeBlock[]{HYDROGEN.get(), CHLORINE.get()}) {
            event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> pipe.handler(level, pos, side), pipe);
        }
    }
}
