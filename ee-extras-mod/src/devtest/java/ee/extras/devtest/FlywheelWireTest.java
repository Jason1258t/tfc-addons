package ee.extras.devtest;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.config.CEEConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Провода EE через Flywheel на клиентском стенде (файл flywheel-wires в папке игры, мир flat): сеть Wire.build и тонкие
 * провода, в лог — backend Flywheel и замер WireDiag, кадр с Flywheel и тот же кадр базовым рендером EE.
 */
final class FlywheelWireTest {
    static final boolean ON = java.nio.file.Files.exists(java.nio.file.Path.of("flywheel-wires"));

    static void tick(Minecraft mc, IntegratedServer server, int tick) throws Exception {
        if (tick == 40) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            p.teleportTo(p.serverLevel(), 6.5, -55, 190.5, 0f, 35f);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.serverLevel().setDayTime(6000);
            server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        });
        if (tick == 80) server.execute(() -> {
            var src = server.getPlayerList().getPlayer(mc.player.getUUID()).createCommandSourceStack();
            Wire.build(src);
            Wire.thin(src);
        });
        if (tick == 90) mc.options.hideGui = true;
        if (tick == 160) {
            WireClientTest.look(mc, new Vec3(6.5, -59, 204));
            var manager = Class.forName("dev.engine_room.flywheel.api.backend.BackendManager");
            Object backend = manager.getMethod("currentBackend").invoke(null);
            System.out.println("EECLIENT FLYWHEEL backend=" + backend + " flywheelWires="
                    + !CEEConfigs.client().disableFlywheelWireRendering.get() + " gl="
                    + com.mojang.blaze3d.platform.GlUtil.getRenderer());
            var report = ee.extras.WireDiag.class.getDeclaredMethod("report");
            report.setAccessible(true);
            report.invoke(null);
        }
        if (tick == 170) Screenshot.grab(mc.gameDirectory, "flywheel-wires-flywheel.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 175) {
            CEEConfigs.client().disableFlywheelWireRendering.set(true);
            WireRenderer.recreateVisuals();
        }
        if (tick == 195) Screenshot.grab(mc.gameDirectory, "flywheel-wires-basic.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 200) {
            CEEConfigs.client().disableFlywheelWireRendering.set(false);
            WireRenderer.recreateVisuals();
        }
        if (tick == 220) Screenshot.grab(mc.gameDirectory, "flywheel-wires-flywheel-again.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 240) {
            System.out.println("EECLIENT FLYWHEEL ИТОГ");
            mc.stop();
        }
    }

    private FlywheelWireTest() {
    }
}
