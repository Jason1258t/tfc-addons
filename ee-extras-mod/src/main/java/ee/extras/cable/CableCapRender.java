package ee.extras.cable;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ee.extras.EeExtras;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class CableCapRender {
    private static final Map<Level, Map<Long, Set<BlockPos>>> CAPS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final RenderType FRONT = RenderType.entityCutoutNoCull(EeExtras.rl("textures/block/duplex_cap_front.png"));
    private static final RenderType SIDE = RenderType.entityCutoutNoCull(EeExtras.rl("textures/block/duplex_cap_side.png"));

    public static void register(IEventBus bus) {
        bus.addListener(CableCapRender::render);
        bus.addListener(CableCapRender::unload);
    }

    static void sync(Level level, CableCapSync packet) {
        long chunk = packet.data().getLong("Chunk");
        var chunks = CAPS.computeIfAbsent(level, key -> new ConcurrentHashMap<>());
        Set<BlockPos> positions = new HashSet<>();
        for (long pos : packet.data().getLongArray("Caps")) positions.add(BlockPos.of(pos));
        if (positions.isEmpty()) chunks.remove(chunk);
        else chunks.put(chunk, positions);
    }

    static boolean has(Level level, BlockPos pos) {
        return CAPS.getOrDefault(level, Map.of()).getOrDefault(new ChunkPos(pos).toLong(), Set.of()).contains(pos);
    }

    /** Только оболочка кабеля выходит из крышки. Координаты узлов для симуляции остаются у EE. */
    public static Vec3 cableInlet(Level level, InWorldNode node, WireType wire) {
        if (wire != CableWires.DUPLEX.get() && wire != CableWires.THIN.get()) return null;
        BlockPos pos = node.sourcePos();
        if (!has(level, pos)) return null;
        var state = level.getBlockState(pos);
        int[] contacts = CableCap.contacts(level, pos, state);
        if (contacts == null || contacts.length != 2 || contacts[0] != node.id()) return null;
        var geometry = CableCapGeometry.of(level, pos, state, contacts);
        return geometry == null ? null : Vec3.atLowerCornerOf(pos).add(geometry.inlet());
    }

    private static void unload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide && CAPS.containsKey(level))
            CAPS.get(level).remove(event.getChunk().getPos().toLong());
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        var chunks = CAPS.get(mc.level);
        if (chunks == null || chunks.isEmpty()) return;
        var buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        for (var positions : chunks.values()) {
            for (BlockPos pos : positions) {
                if (event.getFrustum().isVisible(new AABB(pos).inflate(0.5)))
                    draw(mc.level, pos, camera, event.getPoseStack(), buffers);
            }
        }
        buffers.endBatch(FRONT);
        buffers.endBatch(SIDE);
    }

    private static void draw(Level level, BlockPos pos, Vec3 camera, PoseStack pose,
                             net.minecraft.client.renderer.MultiBufferSource buffers) {
        var state = level.getBlockState(pos);
        int[] contacts = CableCap.contacts(level, pos, state);
        if (contacts == null || contacts.length != 2) return;
        var geometry = CableCapGeometry.of(level, pos, state, contacts);
        if (geometry == null) return;
        Vec3 front = geometry.front();
        Vec3 back = geometry.back();
        Vec3 outward = geometry.outward();
        Vec3 x = geometry.length();
        Vec3 y = geometry.width();
        Vec3[] top = corners(front, x, y);
        Vec3[] bottom = corners(back, x, y);
        Vec3 offset = Vec3.atLowerCornerOf(pos).subtract(camera);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(Vec3.atLowerCornerOf(pos).add(front)));
        quad(pose.last(), buffers.getBuffer(FRONT), light, outward, top[0], top[1], top[2], top[3]);
        var side = buffers.getBuffer(SIDE);
        quad(pose.last(), side, light, outward.scale(-1), bottom[3], bottom[2], bottom[1], bottom[0]);
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            Vec3 normal = top[next].subtract(top[i]).cross(outward).normalize();
            quad(pose.last(), side, light, normal, top[i], bottom[i], bottom[next], top[next]);
        }
        pose.popPose();
    }

    private static Vec3[] corners(Vec3 center, Vec3 x, Vec3 y) {
        return new Vec3[]{center.subtract(x).subtract(y), center.add(x).subtract(y),
                center.add(x).add(y), center.subtract(x).add(y)};
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, Vec3 normal, Vec3... corners) {
        for (int i = 0; i < 4; i++) {
            Vec3 point = corners[i];
            buffer.addVertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                    .setColor(255, 255, 255, 255).setUv(i == 0 || i == 3 ? 0 : 1, i < 2 ? 0 : 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    private CableCapRender() {
    }
}
