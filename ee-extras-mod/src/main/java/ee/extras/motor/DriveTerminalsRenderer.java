package ee.extras.motor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import ee.extras.EeExtras;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class DriveTerminalsRenderer {
    private static final PartialModel TERMINALS = PartialModel.of(EeExtras.rl("block/drive_terminals"));
    private static final Set<KineticBlockEntity> MOUNTS = Collections.newSetFromMap(new WeakHashMap<>());

    public static void register(IEventBus bus) {
        bus.addListener(DriveTerminalsRenderer::render);
    }

    public static void track(KineticBlockEntity entity, boolean installed) {
        if (installed) MOUNTS.add(entity);
        else MOUNTS.remove(entity);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS || MOUNTS.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        MOUNTS.removeIf(entity -> entity.isRemoved() || entity.getLevel() != level);
        var buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        for (var entity : MOUNTS) {
            Direction side = ((BeltMotorHost) entity).eeExtras$mountSide();
            var pos = entity.getBlockPos();
            if (side == null || !event.getFrustum().isVisible(new AABB(pos).inflate(0.125))) continue;
            var pose = event.getPoseStack();
            Vec3 offset = Vec3.atLowerCornerOf(pos).subtract(camera);
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            draw(entity, side, pose, buffers, LevelRenderer.getLightColor(level, pos.relative(side)));
            pose.popPose();
        }
        buffers.endBatch(RenderType.cutout());
    }

    private static void draw(BlockEntity entity, Direction side, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        switch (side) {
            case NORTH -> { }
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
        }
        pose.translate(-0.5, -0.5, -0.5);
        CachedBuffers.partial(TERMINALS, entity.getBlockState()).light(light)
                .renderInto(pose, buffers.getBuffer(RenderType.cutout()));
        pose.popPose();
    }
}
