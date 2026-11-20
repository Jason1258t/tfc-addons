package ee.extras.devtest;

import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import tfc.stand.scene.Scene;

/**
 * Фото платы в сохранённом мире после рестарта (dev #358, цепочка pcb-restart): плату ставит сервер ({@code rectest
 * pcbmount}), мир переживает рестарт, клиент входит в него и сцена снимает плату. Площадку сцена не строит — середина
 * сцены — точка появления мира, плата — над ней (как в {@link PcbMount}). Без мира (stand_submit scene=pcb-world) сцена
 * сама ставит ту же плату над серединой своей площадки.
 */
final class PcbWorldScene {
    static final String NAME = "pcb-world";

    private PcbWorldScene() {}

    static void start() {
        Scene scene = new Scene("pcb-world");
        int x = PcbMount.DX, y = PcbMount.DY, z = PcbMount.DZ;
        if (!Scene.inWorld())
            scene.server("плата над площадкой", c -> scene.note("плата: установка " + PcbMount.mount(c.level(), c.at(x, y - 1, z))));
        scene.gui(false);
        // Сначала камера к плате: до неё клиент может не держать её чанк
        scene.camera(x, y + 0.1, z, 1.6, 30, 55);
        scene.until("плата у клиента", 600, () -> {
            var state = Minecraft.getInstance().level.getBlockState(scene.origin().offset(x, y, z));
            return state.is(PcbItems.CIRCUIT_BLOCK.get()) && state.getValue(PcbCircuitBlock.FACING) == Direction.UP;
        });
        scene.check("плата у клиента с блок-сущностью", () -> Minecraft.getInstance().level
                .getBlockEntity(scene.origin().offset(x, y, z)) != null);
        // Название области TFC гаснет само
        scene.ticks(100);
        scene.shot("board");
        scene.camera(x, y + 0.1, z, 3, 200, 35);
        scene.shot("board-side");
        scene.start();
    }
}
