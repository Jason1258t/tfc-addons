package ie.extras.devtest;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import tfc.stand.scene.Scene;

@EventBusSubscriber(modid = "ie_extras_devtest", value = Dist.CLIENT)
public final class BeamhouseScene {
    private static final String NAME = "beamhouse-ports";
    private static boolean started;

    @SubscribeEvent
    public static void open(ScreenEvent.Init.Post event) {
        if (started || !Scene.requested(NAME) || !(event.getScreen() instanceof TitleScreen)) return;
        started = true;
        Scene scene = new Scene("beamhouse-ports").area(22, 8).gui(false);
        scene.server("собрать барабаны во всех ориентациях", ctx -> {
            int i = 0;
            for (Direction front : Direction.Plane.HORIZONTAL) {
                for (boolean mirror : new boolean[]{false, true}) {
                    int x = i == 0 ? -6 : i == 1 ? 6 : (i % 4) * 9 - 13;
                    int z = i < 2 ? 0 : i < 4 ? -11 : 12;
                    scene.check(front + " зеркало=" + mirror + ": предметный и жидкостный входы",
                            BeamhouseCheck.verify(ctx.level(), ctx.player(), ctx.at(x, 0, z), front, mirror));
                    i++;
                }
            }
        });
        scene.camera(-4, 0.8, 1.7, 5.5, 110, 12).shot("item-input");
        scene.camera(4.6, 0.9, 2.5, 5.5, -150, 16).shot("mirrored-ports").start();
    }
}
