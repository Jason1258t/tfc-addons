package ee.extras.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import ee.extras.EeExtras;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class FluidDispenserRenderer extends SafeBlockEntityRenderer<FluidDispenserBlockEntity> {
    private FluidDispenserRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(EeExtras.FLUID_DISPENSER_BE.get(), FluidDispenserRenderer::new));
    }

    @Override
    protected void renderSafe(FluidDispenserBlockEntity dispenser, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        FilteringRenderer.renderOnBlockEntity(dispenser, partialTicks, pose, buffers, light, overlay);
    }
}
