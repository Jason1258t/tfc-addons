package ee.extras.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import ee.extras.EeExtras;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class MeteringPumpRenderer extends SafeBlockEntityRenderer<MeteringPumpBlockEntity> {
    private MeteringPumpRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(EeExtras.METERING_PUMP_BE.get(), MeteringPumpRenderer::new));
    }

    @Override
    protected void renderSafe(MeteringPumpBlockEntity dispenser, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        FilteringRenderer.renderOnBlockEntity(dispenser, partialTicks, pose, buffers, light, overlay);
    }
}
