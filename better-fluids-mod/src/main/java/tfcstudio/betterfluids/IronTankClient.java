package tfcstudio.betterfluids;

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import tfcstudio.betterfluids.mixin.TankModelInvoker;

@EventBusSubscriber(modid = BetterFluids.ID, value = Dist.CLIENT)
final class IronTankClient {
    private static final CTSpriteShiftEntry SIDE = shift(AllSpriteShifts.FLUID_TANK, "iron_tank");
    private static final CTSpriteShiftEntry TOP = shift(AllSpriteShifts.FLUID_TANK_TOP, "iron_tank_top");
    private static final CTSpriteShiftEntry INNER = shift(AllSpriteShifts.FLUID_TANK_INNER, "iron_tank_inner");

    @SubscribeEvent
    static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BetterFluids.TANK_ENTITY.get(), IronTankRenderer::new);
    }

    @SubscribeEvent
    static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((id, model) ->
            id.id().equals(ResourceLocation.fromNamespaceAndPath(BetterFluids.ID, "iron_tank"))
                && !id.variant().equals("inventory")
                ? TankModelInvoker.create(new WindowTankModel(model), SIDE, TOP, INNER)
                : model);
    }

    private static CTSpriteShiftEntry shift(CTSpriteShiftEntry template, String name) {
        var sprite = ResourceLocation.fromNamespaceAndPath(BetterFluids.ID, "block/" + name);
        return CTSpriteShifter.getCT(template.getType(), sprite, sprite.withSuffix("_connected"));
    }
}
