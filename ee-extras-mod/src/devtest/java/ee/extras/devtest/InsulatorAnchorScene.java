package ee.extras.devtest;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import ee.extras.lines.InsulatorAnchors;
import net.minecraft.world.level.block.Blocks;
import tfc.stand.scene.Scene;

final class InsulatorAnchorScene {
    static final boolean ON = Scene.requested("insulator-anchors");

    static void start() {
        Scene scene = new Scene("insulator-anchors").area(25, 14);
        InWorldNodeConnection[] span = new InWorldNodeConnection[1];
        scene.server("две опоры с обычными гирляндами", c -> {
            for (int x : new int[] { -9, 9 }) {
                for (int y = 0; y <= 9; y++) c.set(c.at(x, y, -2), Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int z = -1; z <= 0; z++) c.set(c.at(x, 9, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
            span[0] = InsulatorAnchorTest.build(c.level(), c.at(-9, 8, 0), c.at(9, 8, 0));
        });
        scene.until("пролёт виден клиенту", 300, () -> WireRenderer.getAllConnections().size() >= 3);
        scene.gui(false);
        scene.camera(0, 5.5, 0, 24, 180, 7);
        scene.ticks(100).shot("free-suspension");
        scene.server("закрепить обе гирлянды", c -> {
            InsulatorAnchorTest.clamp(c.level(), span[0], c.player(), 0.05f);
            InsulatorAnchorTest.clamp(c.level(), span[0], c.player(), 0.95f);
        });
        scene.until("клиент получил зажимы", 200, () -> WireRenderer.getAllConnections().stream()
                .anyMatch(connection -> InsulatorAnchors.hasAnchor(connection.getSecond())
                        && connection.getSecond().attachments().size() == 2));
        scene.ticks(25);
        scene.server("обе гирлянды наклонены", c -> scene.check("наклон 60 градусов", InsulatorAnchorTest.shape(
                c.level(), c.at(-9, 8, 0), c.at(9, 8, 0), true)));
        scene.shot("anchored-span").start();
    }
}
