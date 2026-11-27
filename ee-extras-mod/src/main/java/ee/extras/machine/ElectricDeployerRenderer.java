package ee.extras.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import ee.extras.EeExtras;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ElectricDeployerRenderer extends DeployerRenderer {
    private static final PartialModel POLE = PartialModel.of(EeExtras.rl("block/electric_deployer_pole"));

    private static final PartialModel COLLAR = PartialModel.of(EeExtras.rl("block/electric_deployer_collar"));

    private ElectricDeployerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void register(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(EeExtras.ELECTRIC_DEPLOYER_BE.get(), ElectricDeployerRenderer::new));
    }

    @Override
    protected void renderSafe(DeployerBlockEntity deployer, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        renderItem(deployer, partialTicks, pose, buffers, light, overlay);
        FilteringRenderer.renderOnBlockEntity(deployer, partialTicks, pose, buffers, light, overlay);

        MachineControlsRenderer.render((MotorMachine) deployer, partialTicks, pose, buffers, light, overlay);
        var state = deployer.getBlockState();
        var facing = state.getValue(DirectionalAxisKineticBlock.FACING);
        var offset = getHandOffset(deployer, partialTicks, state);
        var buffer = buffers.getBuffer(RenderType.solid());
        renderPole(deployer, partialTicks, pose, light, state, facing, buffer);
        orient(CachedBuffers.partial(COLLAR, state).translate(offset.x, offset.y, offset.z), state, facing, true)
                .light(light).renderInto(pose, buffer);
        orient(CachedBuffers.partial(deployer.getHandPose(), state).translate(offset.x, offset.y, offset.z),
                state, facing, false).light(light).renderInto(pose, buffer);
    }

    private static void renderPole(DeployerBlockEntity deployer, float partialTicks, PoseStack pose, int light,
            BlockState state, Direction facing, VertexConsumer buffer) {
        float stretch = 1 + deployer.getHandOffset(partialTicks) / (8f / 16);
        Vec3 anchor = new Vec3(0.5, 0.5, 0.5).subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.25));
        pose.pushPose();
        pose.translate(anchor.x, anchor.y, anchor.z);
        pose.scale(facing.getAxis() == Direction.Axis.X ? stretch : 1,
                facing.getAxis() == Direction.Axis.Y ? stretch : 1,
                facing.getAxis() == Direction.Axis.Z ? stretch : 1);
        pose.translate(-anchor.x, -anchor.y, -anchor.z);
        orient(CachedBuffers.partial(POLE, state), state, facing, true).light(light).renderInto(pose, buffer);
        pose.popPose();
    }

    private static SuperByteBuffer orient(SuperByteBuffer buffer, BlockState state, Direction facing, boolean pole) {
        float pitch = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
        boolean roll = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE)
                ^ facing.getAxis() == Direction.Axis.Z;
        buffer.rotateCentered((float) Math.toRadians(AngleHelper.horizontalAngle(facing)), Direction.UP);
        buffer.rotateCentered((float) Math.toRadians(pitch), Direction.EAST);
        if (pole && roll) buffer.rotateCentered((float) Math.PI / 2, Direction.SOUTH);
        return buffer;
    }
}
