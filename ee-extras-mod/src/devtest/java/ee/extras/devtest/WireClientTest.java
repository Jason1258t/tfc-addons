package ee.extras.devtest;

import ee.extras.wire.SurfaceWireEntity;
import ee.extras.wire.SurfaceWireItem;
import ee.extras.wire.SurfaceWires;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Декоративный провод в настоящем клиенте (WIRE=1 ./test-client.sh): сущности и отрезки доходят до клиента, наведение
 * на отрезок (миксин ProjectileUtil), ПКМ по середине провода настоящей клавишей — выбор узла ответвления, снимки.
 */
final class WireClientTest {
    static Vec3 aim;

    static void look(Minecraft mc, Vec3 at) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 d = at.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    static void tick(Minecraft mc, IntegratedServer server, int tick) {
        if (tick == 40) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            p.teleportTo(p.serverLevel(), 7.5, -57.5, 196.5, 0f, 45f);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.serverLevel().setDayTime(6000);
            server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        });
        if (tick == 80) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            Wire.build(p.createCommandSourceStack());
            Wire.thin(p.createCommandSourceStack());
            Wire.power(p.createCommandSourceStack(), 230);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SurfaceWires.DUPLEX_ITEM.get(), 32));
        });
        if (tick == 120) {
            List<SurfaceWireEntity> ws = mc.level.getEntitiesOfClass(SurfaceWireEntity.class, new AABB(Wire.BAT).inflate(40));
            ClientTest.check("у клиента 4 куска провода (" + ws.size() + ")", ws.size() == 4);
            ClientTest.check("отрезки дошли до клиента", !ws.isEmpty() && ws.stream().allMatch(w -> !w.segments.isEmpty() && w.totalLength() > 1));
            Screenshot.grab(mc.gameDirectory, "wire_overview.png", mc.getMainRenderTarget(), c -> {});
            SurfaceWireEntity ab = ws.stream().filter(w -> w.totalLength() > 7 && w.totalLength() < 9).findFirst().orElse(null);
            if (ab != null) {
                aim = ab.at(ab.gridLength() / 2);
                Vec3 to = aim.add(0, 0.2, -1.2);
                server.execute(() -> {
                    var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                    p.teleportTo(p.serverLevel(), to.x, to.y, to.z, 0f, 60f);
                });
            }
        }
        if (tick == 126 && aim != null) look(mc, aim);
        if (tick == 130) {
            boolean hit = mc.hitResult instanceof EntityHitResult e && e.getEntity() instanceof SurfaceWireEntity;
            ClientTest.check("наведение на провод с проводом в руке (" + mc.hitResult + ")", hit);
            KeyMapping.click(mc.options.keyUse.getKey());
        }
        if (tick == 150) {
            ClientTest.check("ПКМ по проводу выбрал точку ответвления", SurfaceWireItem.selection(mc.player.getMainHandItem()) != null);
            look(mc, new Vec3(9.5, -60, 197.5));
        }
        if (tick == 160) Screenshot.grab(mc.gameDirectory, "wire_preview.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 170) {
            mc.player.getInventory().items.set(mc.player.getInventory().selected, ItemStack.EMPTY);
            server.execute(() -> server.getPlayerList().getPlayer(mc.player.getUUID()).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        }
        if (tick == 180 && aim != null) {
            look(mc, aim);
        }
        if (tick == 190) {
            boolean hit = mc.hitResult instanceof EntityHitResult e && e.getEntity() instanceof SurfaceWireEntity;
            ClientTest.check("с пустой рукой провод не загораживает блок", !hit);
            Screenshot.grab(mc.gameDirectory, "wire_close.png", mc.getMainRenderTarget(), c -> {});
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                p.teleportTo(p.serverLevel(), 4.5, -57.5, 246.5, 0f, 35f);
            });
        }
        if (tick == 205) look(mc, new Vec3(5.5, -59.5, 251.5));
        if (tick == 215) {
            Screenshot.grab(mc.gameDirectory, "wire_thin.png", mc.getMainRenderTarget(), c -> {});
            System.out.println("EECLIENT WIRE ИТОГ");
            mc.stop();
        }
    }

    private WireClientTest() {
    }
}
