package ee.extras.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import ee.extras.EeExtras;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ElectricPressRenderer extends SafeBlockEntityRenderer<ElectricPressBlockEntity> {
    private static final PartialModel HEAD = PartialModel.of(EeExtras.rl("block/electric_press_head"));
    private static final PartialModel ROD = PartialModel.of(EeExtras.rl("block/electric_press_rod"));

    private ElectricPressRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(EeExtras.ELECTRIC_PRESS_BE.get(), ElectricPressRenderer::new));
    }

    @Override
    public boolean shouldRenderOffScreen(ElectricPressBlockEntity press) {
        return true;
    }

    @Override
    protected void renderSafe(ElectricPressBlockEntity press, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var state = press.getBlockState();
        PressingBehaviour behaviour = press.getPressingBehaviour();
        float offset = behaviour.getRenderedHeadOffset(partialTicks) * behaviour.mode.headOffset;
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        var buffer = buffers.getBuffer(RenderType.solid());
        CachedBuffers.partialFacing(HEAD, state, facing)
                .translate(0, -offset, 0).light(light).renderInto(pose, buffer);

        // Нижний конец следует за плитой, верхний всегда остаётся внутри корпуса.
        float rodScale = 1 + offset / (10f / 16);
        pose.pushPose();
        pose.translate(0, 4f / 16 - offset, 0);
        pose.scale(1, rodScale, 1);
        pose.translate(0, -4f / 16, 0);
        CachedBuffers.partialFacing(ROD, state, facing).light(light).renderInto(pose, buffer);
        pose.popPose();
    }
}
