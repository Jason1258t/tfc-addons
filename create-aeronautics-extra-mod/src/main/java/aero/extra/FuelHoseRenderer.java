package aero.extra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

final class FuelHoseRenderer<T extends FuelEndpointBlockEntity> implements BlockEntityRenderer<T> {
    private static final ResourceLocation CASING = ResourceLocation.parse("create:block/andesite_casing");
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    FuelHoseRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(T endpoint) {
        return true;
    }

    @Override
    public void render(T endpoint, float partialTick, PoseStack matrices, MultiBufferSource buffers,
                       int light, int overlay) {
        if (endpoint.hoseEnds().isEmpty()) return;
        var level = endpoint.getLevel();
        BlockPos start = endpoint.getBlockPos();
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(CASING);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Vec3 origin = Vec3.atLowerCornerOf(start);
        for (BlockPos end : endpoint.hoseEnds()) {
            if (start.compareTo(end) >= 0 || !level.hasChunkAt(end)
                    || !(level.getBlockEntity(end) instanceof FuelEndpointBlockEntity other)
                    || !other.hoseEnds().contains(start)) continue;
            int segments = Math.max(8, (int) Math.ceil(Math.sqrt(start.distSqr(end)) * 8));
            Vec3 delta = Vec3.atLowerCornerOf(end.subtract(start)).normalize();
            Vec3 lateral = delta.cross(Math.abs(delta.y) > 0.95 ? EAST : UP).normalize();
            Vec3 vertical = lateral.cross(delta).normalize();
            Vec3[] range = {lateral, vertical, lateral.reverse(), vertical.reverse()};
            Vec3 previous = FuelHoses.curve(start, end, 0);
            for (int i = 1; i <= segments; i++) {
                Vec3 current = FuelHoses.curve(start, end, (double) i / segments);
                int segmentLight = LevelRenderer.getLightColor(level, BlockPos.containing(current));
                ringSegment(vertices, matrices.last(), sprite, origin, previous, current, range, segmentLight, overlay, i);
                previous = current;
            }
        }
    }

    private static void ringSegment(VertexConsumer vertices, PoseStack.Pose pose, TextureAtlasSprite sprite, Vec3 origin,
                                    Vec3 start, Vec3 end, Vec3[] directions, int light, int overlay, int index) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int color = index % 2 == 0 ? 0xff9a9a9a : 0xff5e5e5e;
        for (int side = 0; side < directions.length; side++) {
            Vec3 a = directions[side];
            Vec3 b = directions[(side + 1) % directions.length];
            Vec3 normal = a.add(b).normalize();
            Vec3 offsetA = a.scale(0.075).subtract(origin);
            Vec3 offsetB = b.scale(0.075).subtract(origin);
            point(vertices, pose, start.add(offsetA), normal, u0, v0, color, light, overlay);
            point(vertices, pose, start.add(offsetB), normal, u1, v0, color, light, overlay);
            point(vertices, pose, end.add(offsetB), normal, u1, v1, color, light, overlay);
            point(vertices, pose, end.add(offsetA), normal, u0, v1, color, light, overlay);
        }
    }

    private static void point(VertexConsumer vertices, PoseStack.Pose pose, Vec3 pos, Vec3 normal, float u, float v,
                              int color, int light, int overlay) {
        vertices.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z).setColor(color).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
