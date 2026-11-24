package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;

import ee.extras.panel.PanelInletGeometry;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Проверяет фактические baked-вершины клиента, без мира, камеры и скриншотов. */
final class PanelModelProbe {
    static void run() {
        try {
            check();
        } finally {
            Minecraft.getInstance().stop();
        }
    }

    private static void check() {
        var fixed = PanelInletGeometry.modelState();
        var original =
                CEEBlocks.DUPLEX_WIRE_TERMINATION
                        .getDefaultState()
                        .setValue(
                                net.minecraft.world.level.block.state.properties
                                        .BlockStateProperties.FACING,
                                Direction.UP);
        int count = 0;
        for (var slot : ElectricalPanelSlot.values()) {
            if (!slot.name().startsWith("QUARTER_") || slot == ElectricalPanelSlot.QUARTER_CENTER)
                continue;
            var before = bounds(original, slot, false);
            var after = bounds(fixed, slot, false);
            var body = bounds(fixed, slot, true);
            boolean top = PanelInletGeometry.upper(slot);
            double edge = top ? body[1] : body[0];
            if (Math.abs(edge - (top ? 1 : 0)) > 1e-6)
                throw new IllegalStateException("MODELPROBE FAIL: зазор у " + slot + ": " + edge);
            double tip = top ? after[1] : after[0];
            if (Math.abs(tip - (top ? 17.0 / 16 : -1.0 / 16)) > 1e-6)
                throw new IllegalStateException("MODELPROBE FAIL: неверный конец патрубка " + tip);
            double oldEdge = top ? before[1] : before[0];
            if (Math.abs(oldEdge - (top ? 1 : 0)) < .01)
                throw new IllegalStateException(
                        "MODELPROBE FAIL: отрицательный контроль не обнаружил FLIP");
            System.out.println(
                    "MODELPROBE OK "
                            + slot
                            + ": было y="
                            + before[0] * 16
                            + ".."
                            + before[1] * 16
                            + ", стало y="
                            + after[0] * 16
                            + ".."
                            + after[1] * 16
                            + " px");
            checkContacts(fixed, slot);
            count++;
        }
        System.out.println(
                "MODELPROBE PASS: "
                        + count
                        + " ввода, корпус прижат к y=0/16, патрубок снаружи y=-1/17; прежний FLIP"
                        + " даёт зазор");
    }

    private static void checkContacts(BlockState state, ElectricalPanelSlot slot) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        int contacts = 0;
        for (var face :
                new Direction[] {
                    null,
                    Direction.DOWN,
                    Direction.UP,
                    Direction.NORTH,
                    Direction.SOUTH,
                    Direction.WEST,
                    Direction.EAST
                })
            for (var quad : model.getQuads(state, face, RandomSource.create(0))) {
                if (quad.getDirection() != Direction.NORTH) continue;
                var data = quad.getVertices();
                int stride = data.length / 4;
                boolean copper = true;
                for (int i = 0; i < 4; i++)
                    copper &=
                            Math.abs(Float.intBitsToFloat(data[i * stride + 2]) - 7.0 / 16) < 1e-6;
                if (!copper) continue;
                var point =
                        new Vec3(
                                Float.intBitsToFloat(data[0]),
                                Float.intBitsToFloat(data[1]),
                                Float.intBitsToFloat(data[2]));
                var normal =
                        PanelInletGeometry.modelPoint(slot, point.add(0, 0, -1))
                                .subtract(PanelInletGeometry.modelPoint(slot, point));
                if (PanelInletGeometry.upper(slot) ? normal.y >= 0 : normal.y <= 0)
                    throw new IllegalStateException(
                            "MODELPROBE FAIL: контакты направлены наружу " + slot);
                contacts++;
            }
        if (contacts != 2)
            throw new IllegalStateException(
                    "MODELPROBE FAIL: не найдены две внутренние контактные грани " + contacts);
    }

    private static double[] bounds(BlockState state, ElectricalPanelSlot slot, boolean bodyOnly) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int count = 0;
        var faces =
                new Direction[] {
                    null,
                    Direction.DOWN,
                    Direction.UP,
                    Direction.NORTH,
                    Direction.SOUTH,
                    Direction.WEST,
                    Direction.EAST
                };
        for (var face : faces)
            for (var quad : model.getQuads(state, face, RandomSource.create(0))) {
                var data = quad.getVertices();
                int stride = data.length / 4;
                for (int i = 0; i < 4; i++) {
                    var point =
                            new Vec3(
                                    Float.intBitsToFloat(data[i * stride]),
                                    Float.intBitsToFloat(data[i * stride + 1]),
                                    Float.intBitsToFloat(data[i * stride + 2]));
                    if (bodyOnly && point.z > 12.0 / 16 + 1e-6) continue;
                    var transformed = PanelInletGeometry.modelPoint(slot, point);
                    min = Math.min(min, transformed.y);
                    max = Math.max(max, transformed.y);
                    count++;
                }
            }
        if (count == 0) throw new IllegalStateException("MODELPROBE FAIL: пустая baked-модель");
        return new double[] {min, max};
    }

    private PanelModelProbe() {}
}
