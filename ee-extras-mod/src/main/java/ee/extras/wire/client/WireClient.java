package ee.extras.wire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ee.extras.wire.BlockTrace;
import ee.extras.wire.Point;
import ee.extras.wire.SurfaceWireEntity;
import ee.extras.wire.SurfaceWireItem;
import ee.extras.wire.SurfaceWires;
import ee.extras.wire.WireConfig;
import ee.extras.wire.WireEnd;
import ee.extras.wire.WireEvents;
import ee.extras.wire.WirePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент провода: данные с сервера, клик по проводу (ответвление, сращивание), кусачки в два клика с подсветкой
 * участка, предпросмотр пути и остатка предметов, клавиша «Прямая укладка».
 *
 * Перенесено из Create: Power Grid 0.6.2 (ClientWireInteractions, WirePreview, WirePreviewImpl; Apache-2.0, см.
 * NOTICE). Изменения: без Sable и шнуров; предмет и длина — в строке над хотбаром вместо PlacementOverlay Create.
 */
public final class WireClient {
    public static final KeyMapping ALTERNATE = new KeyMapping("key.ee_extras.alternate_wire", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT, "key.categories.ee_extras");
    private static boolean alternateSent;

    private static SurfaceWireEntity cutWire;
    private static int cutFrom;
    private static final RandomSource RANDOM = RandomSource.create();

    private static BlockTrace.Result preview;
    private static Vec3 previewStart;
    private static float previewThickness;

    public static void register(IEventBus modBus, IEventBus bus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(SurfaceWires.WIRE_ENTITY.get(), WireRenderer::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ALTERNATE));
        bus.addListener((ClientTickEvent.Post e) -> tick());
        bus.addListener(WireClient::render);
    }

    public static boolean alternateDown() {
        return ALTERNATE.isDown();
    }

    public static void sync(WirePackets.Sync p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(p.entity()) instanceof SurfaceWireEntity w) w.readClientData(p.data());
    }

    /** Шаг сетки на проводе под курсором: {номер отрезка, шаг от его начала} */
    static int[] segmentAt(SurfaceWireEntity w, Vec3 hit) {
        Vec3 local = hit.subtract(w.position());
        float t = w.spec().thickness();
        if (w.segments.size() != w.boxes.size()) return null;
        for (int i = 0; i < w.boxes.size(); i++) {
            if (!w.boxes.get(i).inflate(t * 0.2f).contains(local)) continue;
            Point s = w.segments.get(i);
            double d = switch (s.direction.getAxis()) {
                case X -> Math.abs(s.start.x - hit.x);
                case Y -> Math.abs(s.start.y - hit.y);
                case Z -> Math.abs(s.start.z - hit.z);
            };
            return new int[] {i, Mth.clamp((int) Math.round(d * Point.GRID), 0, s.gridLength)};
        }
        return null;
    }

    static int vertex(SurfaceWireEntity w, int[] seg) {
        int v = 0;
        for (int i = 0; i < seg[0]; i++) v += w.segments.get(i).gridLength;
        return v + seg[1];
    }

    public static InteractionResult attach(SurfaceWireEntity w) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != w) return InteractionResult.FAIL;
        ItemStack stack = mc.player.getMainHandItem();
        if (w.item() != stack.getItem()) {
            mc.player.displayClientMessage(Component.translatable("ee_extras.surface_wire.wrong_type").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        int[] seg = segmentAt(w, hit.getLocation());
        if (seg == null) return InteractionResult.FAIL;
        PacketDistributor.sendToServer(new WirePackets.Attach(w.getId(), seg[0], seg[1]));
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult cut(SurfaceWireEntity w) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != w) return InteractionResult.FAIL;
        int[] seg = segmentAt(w, hit.getLocation());
        if (seg == null) return InteractionResult.FAIL;
        if (cutWire != w) {
            cutWire = w;
            cutFrom = vertex(w, seg);
            mc.player.displayClientMessage(Component.translatable("ee_extras.surface_wire.cut_next").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        PacketDistributor.sendToServer(new WirePackets.Cut(w.getId(), cutFrom, vertex(w, seg)));
        cutWire = null;
        return InteractionResult.SUCCESS;
    }

    static ItemStack heldWire(Player p) {
        if (p.getMainHandItem().getItem() instanceof SurfaceWireItem && p.getMainHandItem().has(ee.extras.wire.WireComponents.SELECTION.get()))
            return p.getMainHandItem();
        return null;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        preview = null;
        if (player == null || mc.level == null) return;
        ItemStack stack = heldWire(player);
        boolean alt = stack != null && ALTERNATE.isDown();
        if (alt != alternateSent) {
            alternateSent = alt;
            PacketDistributor.sendToServer(new WirePackets.Alternate(alt));
        }
        if (cutWire != null && (cutWire.isRemoved() || !player.getMainHandItem().is(WireEvents.CUTTERS))) cutWire = null;
        if (cutWire != null && player.isShiftKeyDown()) {
            cutWire = null;
            player.displayClientMessage(Component.translatable("ee_extras.surface_wire.cut_reset"), true);
        }
        cutParticles(mc);
        if (stack != null) previewTick(mc, player, stack);
    }

    static void cutParticles(Minecraft mc) {
        if (cutWire == null || !(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != cutWire) return;
        int[] seg = segmentAt(cutWire, hit.getLocation());
        if (seg == null) return;
        int a = Math.min(cutFrom, vertex(cutWire, seg)), b = Math.max(cutFrom, vertex(cutWire, seg));
        for (int i = 0; i < 4; i++) {
            Vec3 p = cutWire.at(a + RANDOM.nextInt(Math.max(1, b - a + 1))).add((RANDOM.nextFloat() - 0.5) * 0.0625,
                    (RANDOM.nextFloat() - 0.5) * 0.0625, (RANDOM.nextFloat() - 0.5) * 0.0625);
            mc.level.addAlwaysVisibleParticle(new DustParticleOptions(new Vector3f(1f, 0.5f, 0.5f), 0.5f), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    static void previewTick(Minecraft mc, LocalPlayer player, ItemStack stack) {
        HitResult target = mc.hitResult;
        WireEnd sel = SurfaceWireItem.selection(stack);
        if (sel == null || target == null) return;
        if (target.getType() == HitResult.Type.ENTITY && !(((EntityHitResult) target).getEntity() instanceof SurfaceWireEntity)) return;
        if (target.getType() == HitResult.Type.MISS) return;
        Vec3 from = sel.position(mc.level);
        Direction continueDir = null;
        if (sel instanceof WireEnd.WireTip tip) {
            SurfaceWireEntity w = tip.wire(mc.level);
            if (w == null || w.segments.isEmpty()) return;
            continueDir = tip.end() ? w.segments.get(w.segments.size() - 1).direction : w.segments.get(0).direction.getOpposite();
        } else from = BlockTrace.alignPosition(from);
        Vec3 hitPoint = target.getLocation();
        BlockPos device = null;
        if (target instanceof BlockHitResult bh) {
            BlockPos pos = bh.getBlockPos();
            WireEnd.Block dev = new WireEnd.Block(pos);
            if (dev.valid(mc.level)) {
                hitPoint = dev.position(mc.level);
                device = pos;
            } else hitPoint = hitPoint.relative(bh.getDirection(), 0.03125);
        }
        if (from.distanceTo(hitPoint) > 64) return;
        if (ALTERNATE.isDown()) preview = BlockTrace.alternatePath(from, hitPoint);
        else if (device != null) preview = BlockTrace.findPathToDevice(mc.level, from, device, hitPoint, continueDir);
        else preview = BlockTrace.findPath(mc.level, from, hitPoint, null, continueDir);
        previewStart = from;
        previewThickness = SurfaceWires.spec(stack.getItem()).thickness();
        float length = preview == null ? 0 : preview.length();
        float total = length;
        if (sel instanceof WireEnd.WireTip tip && tip.wire(mc.level) != null) total += tip.wire(mc.level).totalLength();
        int items = Math.max(1, WireConfig.items(length));
        boolean ok = preview != null && preview.reachedTarget() && total <= WireConfig.maxLength();
        int have = player.isCreative() ? Integer.MAX_VALUE : count(player, stack);
        player.displayClientMessage(Component.translatable("ee_extras.surface_wire.preview", fmt(total), fmt(WireConfig.maxLength()), items)
                .withStyle(ok && have >= items ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    static int count(Player p, ItemStack like) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(like.getItem())) n += s.getCount();
        return n;
    }

    static String fmt(double v) {
        return String.format("%.1f", v);
    }

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS || preview == null || preview.points().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ms = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SurfaceWires.DUPLEX.texture()));
        ms.pushPose();
        ms.translate(previewStart.x - cam.x, previewStart.y - cam.y, previewStart.z - cam.z);
        int color = preview.reachedTarget() ? 0x80AAFFAA : 0x80FFAAAA;
        Vec3 cur = Vec3.ZERO;
        for (Point p : preview.points()) {
            WireRenderer.renderSegment(ms, vc, 0xF000F0, color, cur, p.direction, previewThickness, p.length(), 0);
            cur = cur.add(p.vector());
        }
        ms.popPose();
        buffers.endBatch(RenderType.entityTranslucent(SurfaceWires.DUPLEX.texture()));
    }

    private WireClient() {
    }
}
