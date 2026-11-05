package ee.extras.wire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ee.extras.wire.Point;
import ee.extras.wire.SurfaceWireEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Отрисовка провода: брусок толщиной spec.thickness на каждый отрезок, первый продлён на шаг назад (к узлу
 * ответвления), свет — наибольший у боковых граней конца отрезка.
 *
 * Перенесено из Create: Power Grid 0.6.2 (BlockWireRenderer, Apache-2.0, см. NOTICE). Изменения: без отрисовки
 * сгоревшего провода (он сразу исчезает), цвет и текстура — из вида провода.
 */
public class WireRenderer extends EntityRenderer<SurfaceWireEntity> {
    public WireRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(SurfaceWireEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(0.5));
    }

    @Override
    public void render(SurfaceWireEntity entity, float yaw, float partialTick, PoseStack ms, MultiBufferSource buffers, int light) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(getTextureLocation(entity)));
        Vec3 pos = entity.position();
        int color = entity.color() < 0 ? 0xFFFFFFFF : entity.color() | 0xFF000000;
        float thickness = entity.spec().thickness();
        Vec3 cur = Vec3.ZERO;
        boolean first = true;
        for (Point s : entity.segments) {
            float length = s.length();
            if (first) {
                cur = Vec3.ZERO.relative(s.direction, -0.0625);
                length += 0.0625f;
                first = false;
            }
            Vec3i n = s.direction.getNormal();
            Vec3 next = cur.add(n.getX() * length, n.getY() * length, n.getZ() * length);
            int l = 0;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == s.direction.getAxis()) continue;
                Vec3i o = d.getNormal();
                BlockPos bp = BlockPos.containing(next.x + pos.x + o.getX() / 16.0, next.y + pos.y + o.getY() / 16.0, next.z + pos.z + o.getZ() / 16.0);
                int packed = LightTexture.pack(entity.level().getBrightness(LightLayer.BLOCK, bp), entity.level().getBrightness(LightLayer.SKY, bp));
                if (packed > l) l = packed;
            }
            renderSegment(ms, buffer, l, color, cur, s.direction, thickness, length, entity.getId());
            cur = next;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SurfaceWireEntity entity) {
        return entity.spec().texture();
    }

    private static void vertex(PoseStack.Pose m, VertexConsumer b, double x, double y, double z, float u, float v, float nx, float ny, float nz, int color, int light) {
        b.addVertex(m.pose(), (float) x, (float) y, (float) z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(m, nx, ny, nz);
    }

    public static void renderSegment(PoseStack ms, VertexConsumer buffer, int light, int color, Vec3 start, Direction dir, float thickness, float length, int uvOffset) {
        thickness *= 1.01f;
        if (dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            length *= -1;
            thickness *= -1;
        }
        uvOffset %= 16;
        float u0 = uvOffset / 16f, v0 = uvOffset / 16f, t0 = uvOffset / 16f;
        float u1 = thickness + uvOffset / 16f, v1 = thickness + uvOffset / 16f, t1 = thickness + uvOffset / 16f;
        ms.pushPose();
        ms.translate(start.x - thickness / 2, start.y - thickness / 2, start.z - thickness / 2);
        double x1 = 0, y1 = 0, z1 = 0, x2 = 0, y2 = 0, z2 = 0;
        switch (dir.getAxis()) {
            case X -> {
                thickness = (float) (thickness * 0.995);
                x1 += thickness;
                x2 = length - thickness;
                u0 = 0;
                u1 = Math.abs(length);
            }
            case Y -> {
                y1 += thickness;
                y2 = length - thickness;
                v0 = 0;
                v1 = Math.abs(length);
            }
            case Z -> {
                thickness = (float) (thickness * 1.005);
                z1 += thickness;
                z2 = length - thickness;
                t0 = 0;
                t1 = Math.abs(length);
            }
        }
        x2 += x1 + thickness;
        y2 += y1 + thickness;
        z2 += z1 + thickness;
        if (dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            double xb = x1, yb = y1, zb = z1;
            x1 = x2;
            y1 = y2;
            z1 = z2;
            x2 = xb;
            y2 = yb;
            z2 = zb;
        }
        PoseStack.Pose m = ms.last();
        vertex(m, buffer, x1, y1, z1, t1, u0, 0, -1, 0, color, light);
        vertex(m, buffer, x2, y1, z1, t1, u1, 0, -1, 0, color, light);
        vertex(m, buffer, x2, y1, z2, t0, u1, 0, -1, 0, color, light);
        vertex(m, buffer, x1, y1, z2, t0, u0, 0, -1, 0, color, light);
        vertex(m, buffer, x1, y2, z1, t0, u1, 0, 1, 0, color, light);
        vertex(m, buffer, x1, y2, z2, t1, u1, 0, 1, 0, color, light);
        vertex(m, buffer, x2, y2, z2, t1, u0, 0, 1, 0, color, light);
        vertex(m, buffer, x2, y2, z1, t0, u0, 0, 1, 0, color, light);
        vertex(m, buffer, x1, y1, z1, t0, v1, -1, 0, 0, color, light);
        vertex(m, buffer, x1, y1, z2, t1, v1, -1, 0, 0, color, light);
        vertex(m, buffer, x1, y2, z2, t1, v0, -1, 0, 0, color, light);
        vertex(m, buffer, x1, y2, z1, t0, v0, -1, 0, 0, color, light);
        vertex(m, buffer, x2, y1, z1, t1, v0, 1, 0, 0, color, light);
        vertex(m, buffer, x2, y2, z1, t1, v1, 1, 0, 0, color, light);
        vertex(m, buffer, x2, y2, z2, t0, v1, 1, 0, 0, color, light);
        vertex(m, buffer, x2, y1, z2, t0, v0, 1, 0, 0, color, light);
        vertex(m, buffer, x1, y1, z1, u0, v0, 0, 0, -1, color, light);
        vertex(m, buffer, x1, y2, z1, u0, v1, 0, 0, -1, color, light);
        vertex(m, buffer, x2, y2, z1, u1, v1, 0, 0, -1, color, light);
        vertex(m, buffer, x2, y1, z1, u1, v0, 0, 0, -1, color, light);
        vertex(m, buffer, x1, y1, z2, u0, v0, 0, 0, 1, color, light);
        vertex(m, buffer, x2, y1, z2, u1, v0, 0, 0, 1, color, light);
        vertex(m, buffer, x2, y2, z2, u1, v1, 0, 0, 1, color, light);
        vertex(m, buffer, x1, y2, z2, u0, v1, 0, 0, 1, color, light);
        ms.popPose();
    }
}
