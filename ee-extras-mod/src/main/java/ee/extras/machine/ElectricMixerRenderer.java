package ee.extras.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import ee.extras.EeExtras;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ElectricMixerRenderer extends SafeBlockEntityRenderer<ElectricMixerBlockEntity> {
    private static final PartialModel COLLAR = PartialModel.of(EeExtras.rl("block/electric_mixer_collar"));
    private static final PartialModel ROD = PartialModel.of(EeExtras.rl("block/electric_mixer_rod"));

    private ElectricMixerRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(EeExtras.ELECTRIC_MIXER_BE.get(), ElectricMixerRenderer::new));
    }

    @Override
    public boolean shouldRenderOffScreen(ElectricMixerBlockEntity mixer) {
        return true;
    }

    @Override
    protected void renderSafe(ElectricMixerBlockEntity mixer, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        MachineControlsRenderer.render((MotorMachine) mixer, partialTicks, pose, buffers, light, overlay);
        var state = mixer.getBlockState();
        float offset = mixer.getRenderedHeadOffset(partialTicks);
        float angle = AnimationTickHolder.getRenderTime(mixer.getLevel())
                * mixer.getRenderedHeadRotationSpeed(partialTicks) * (float) Math.PI / 300;
        var solid = buffers.getBuffer(RenderType.solid());
        CachedBuffers.partial(COLLAR, state).translate(0, -offset, 0).light(light).renderInto(pose, solid);
        pose.pushPose();
        pose.translate(0, 9f / 16 - offset, 0);
        pose.scale(1, 1 + offset / (7f / 16), 1);
        pose.translate(0, -9f / 16, 0);
        CachedBuffers.partial(ROD, state).light(light).renderInto(pose, solid);
        pose.popPose();
        CachedBuffers.partial(AllPartialModels.MECHANICAL_MIXER_HEAD, state)
                .rotateCentered(angle, Direction.UP).translate(0, -offset, 0).light(light)
                .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
    }
}
