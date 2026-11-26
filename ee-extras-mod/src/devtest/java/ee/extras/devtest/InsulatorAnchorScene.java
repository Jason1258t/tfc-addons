package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import ee.extras.lines.InsulatorAnchors;
import ee.extras.lines.Tension;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import tfc.stand.scene.Scene;

final class InsulatorAnchorScene {
    static final boolean ON = Scene.requested("insulator-anchors");

    static void start() {
        Scene scene = new Scene("insulator-anchors").area(25, 14);
        InWorldNodeConnection[] span = new InWorldNodeConnection[1];
        InWorldNodeConnection[] glass = new InWorldNodeConnection[2];
        InWorldNode[] terminals = new InWorldNode[6];
        scene.server("две гирлянды и приборы", c -> {
            if (Scene.inWorld()) {
                // Только копия сохранённого мира: тесту нужна ровная площадка без старой схемы и растительности.
                for (BlockPos pos : BlockPos.betweenClosed(c.at(-25, -1, -25), c.at(25, 14, 25)))
                    c.level().setBlock(pos, pos.getY() < c.origin().getY()
                            ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                                    | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            }
            for (int x : new int[] { -9, 9 }) {
                for (int y = 0; y <= 9; y++) c.set(c.at(x, y, -2), Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int z = -1; z <= 0; z++) c.set(c.at(x, 9, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
            span[0] = InsulatorAnchorTest.build(c.level(), c.at(-9, 8, 0), c.at(9, 8, 0));
            var sd = InfrastructureSavedData.load(c.level());
            var tips = new InWorldNode[] {span[0].node1(), span[0].node2()};
            for (int i = 0; i < 2; i++) {
                glass[i] = sd.getConnections(tips[i]).stream()
                        .filter(w -> InsulatorAnchors.isGlass(sd.getConnectionData(w).wireType())).findFirst().orElseThrow();
            }
            var battery = c.at(-6, 1, 3);
            var meter = c.at(6, 1, 3);
            c.set(battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            c.set(meter, CEEBlocks.VOLTMETER.getDefaultState());
            Lines.ensure(c.level(), battery);
            Lines.ensure(c.level(), meter);
            terminals[0] = new InWorldNode(0, battery);
            terminals[1] = new InWorldNode(1, battery);
            terminals[2] = new InWorldNode(0, meter);
            terminals[3] = new InWorldNode(1, meter);
            var load = c.at(12, 1, 3);
            c.set(load, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
            Lines.ensure(c.level(), load);
            terminals[4] = new InWorldNode(0, load);
            terminals[5] = new InWorldNode(1, load);
        });
        scene.until("пролёт виден клиенту", 300, () -> WireRenderer.getAllConnections().size() >= 3);
        scene.gui(false);
        scene.camera(0, 5.5, 0, 24, 180, 7);
        scene.ticks(100).shot("free-suspension");
        scene.server("два зажима в руке", c -> c.player().setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Tension.CLAMP_ITEM.get(), 2)));
        scene.ticks(10);
        for (int i = 0; i < 2; i++) {
            final int side = i;
            scene.useContact("гирлянда " + i, () -> {
                var mc = Minecraft.getInstance();
                return glass[side].node1().getPosition(mc.level).lerp(glass[side].node2().getPosition(mc.level), 0.5);
            }, () -> new Vec3(0, 0.4, 2), () -> {
                WireInteractionHandler.tick();
                var selected = WireInteractionHandler.targetedPoint;
                return selected != null && selected.connection().equals(glass[side]);
            }, c -> scene.check("сервер обработал зажим " + side,
                    c.player().getMainHandItem().has(CEEDataComponents.SELECTED_NODE) == (side == 0)));
            if (i == 0) {
                scene.check("первая гирлянда сохранена в зажиме", () -> Minecraft.getInstance().player.getMainHandItem()
                        .has(CEEDataComponents.SELECTED_NODE));
                scene.shot("first-string-selected");
            }
        }
        scene.until("оба зажима получены клиентом", 200, () -> {
            var wire = WireRenderer.getConnectionData(span[0]);
            return wire != null && InsulatorAnchors.hasAnchor(wire) && wire.attachments().size() == 2;
        });
        scene.server("геометрия анкерного пролёта", c -> scene.check("наклон 60 градусов",
                InsulatorAnchorTest.shape(c.level(), c.at(-9, 8, 0), c.at(9, 8, 0), true)));
        scene.server("медная катушка в руке", c -> c.player().setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16)));
        scene.ticks(10);
        // Вход начинаем с наконечника, выход заканчиваем на наконечнике — обе последовательности игрока.
        Supplier<InWorldNode[]> route = () -> new InWorldNode[] {
                span[0].node1(), terminals[0], terminals[2], span[0].node2(), terminals[1], terminals[3],
                terminals[2], terminals[4], terminals[3], terminals[5]};
        for (int i = 0; i < 10; i++) {
            final int at = i;
            scene.useContact("катушка " + i, () -> {
                var level = Minecraft.getInstance().level;
                if (at == 0 || at == 3) {
                    var string = glass[at == 0 ? 0 : 1];
                    return string.node1().getPosition(level).lerp(string.node2().getPosition(level), 0.5);
                }
                return route.get()[at].getPosition(level);
            }, () -> {
                var node = route.get()[at];
                if (at == 0 || at == 3) return new Vec3(0, 0.4, 2);
                var point = node.getPosition(Minecraft.getInstance().level);
                var outward = point.subtract(node.sourcePos().getCenter()).normalize().scale(2.5);
                if (outward.y < -0.4) {
                    return outward.horizontalDistanceSqr() < 0.01 ? new Vec3(0, -0.4, 2)
                            : new Vec3(outward.x, -0.4, outward.z);
                }
                return outward;
            }, () -> {
                WireApplyingBehaviour.tick();
                scene.note("цель катушки " + at + ": " + WireApplyingBehaviour.targetingDetachedNode);
                if (at == 0 || at == 3) {
                    return route.get()[at].equals(WireApplyingBehaviour.targetingDetachedNode);
                } else {
                    var mc = Minecraft.getInstance();
                    var hit = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult block
                            ? InWorldNode.getHitNode(block, mc.level) : null;
                    scene.note("клемма " + at + ": нужно " + route.get()[at] + ", выбрана " + hit);
                    return route.get()[at].equals(hit);
                }
            }, c -> {
                if (at % 2 == 1) {
                    var nodes = route.get();
                    scene.check("провод смонтирован после ПКМ " + at,
                            InfrastructureSavedData.load(c.level()).isConnected(nodes[at - 1], nodes[at]));
                }
            });
        }
        scene.ticks(100);
        scene.server("питание через анкерный пролёт", c -> {
            var sd = InfrastructureSavedData.load(c.level());
            scene.check("вход подключён катушкой", sd.isConnected(span[0].node1(), terminals[0]));
            scene.check("выход подключён катушкой", sd.isConnected(span[0].node2(), terminals[2]));
            scene.check("обратный провод подключён катушкой", sd.isConnected(terminals[1], terminals[3]));
            String volts = Extras.vm(com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(c.level()),
                    terminals[2].sourcePos());
            scene.note("напряжение после монтажа " + volts);
            double value = Double.parseDouble(volts.replace(',', '.'));
            scene.check("под нагрузкой более 290 В", value > 290 && value <= 300.1);
        });
        scene.server("убрать катушку", c -> c.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        scene.camera(0, 5.5, 0, 24, 180, 7);
        scene.shot("anchored-span").start();
    }

}
