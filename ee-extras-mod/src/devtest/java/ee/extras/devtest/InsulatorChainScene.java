package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.world.level.block.Blocks;
import tfc.stand.scene.Scene;

final class InsulatorChainScene {
    static void start() {
        Scene scene = new Scene("insulator-chain").area(48, 18);
        InWorldNodeConnection[] spans = new InWorldNodeConnection[2];
        scene.server("два независимых анкерных пролёта", c -> {
            for (int x : new int[] {-20, -2, 2, 20}) {
                for (int y = 0; y <= 9; y++) c.set(c.at(x, y, -2), Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int z = -1; z <= 0; z++) c.set(c.at(x, 9, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
            spans[0] = InsulatorAnchorTest.build(c.level(), c.at(-20, 8, 0), c.at(-2, 8, 0));
            spans[1] = InsulatorAnchorTest.build(c.level(), c.at(2, 8, 0), c.at(20, 8, 0));
            for (var span : spans) InsulatorAnchorTest.clamp(c.level(), span, c.player(), 0.5f);
        });
        scene.ticks(100);
        scene.server("перемычка после натяжения", c -> {
            var sd = InfrastructureSavedData.load(c.level());
            sd.connect(InsulatorAnchorTest.tip(sd, c.at(-2, 8, 0)),
                    InsulatorAnchorTest.tip(sd, c.at(2, 8, 0)), CEEWireTypes.COPPER.get());
        });
        scene.ticks(150);
        scene.server("натяжение сохранилось после перемычки", c -> {
            var sd = InfrastructureSavedData.load(c.level());
            for (var span : spans) scene.check("два зажима на пролёте",
                    sd.getConnectionData(span).attachments().size() == 2);
            scene.check("первый пролёт наклонён", InsulatorAnchorTest.shape(c.level(), c.at(-20, 8, 0), c.at(-2, 8, 0), true));
            scene.check("второй пролёт наклонён", InsulatorAnchorTest.shape(c.level(), c.at(2, 8, 0), c.at(20, 8, 0), true));
        });
        scene.until("клиент получил оба пролёта", 200, () -> {
            for (var span : spans) {
                var wire = WireRenderer.getConnectionData(span);
                if (wire == null || wire.attachments().size() != 2) return false;
            }
            return true;
        });
        scene.gui(false);
        scene.camera(0, 5.5, 0, 43, 180, 7);
        scene.ticks(30).shot("two-anchored-spans");
        scene.camera(0, 6, 0, 15, 180, 10);
        scene.ticks(30).shot("support-jumper").start();
    }
}
