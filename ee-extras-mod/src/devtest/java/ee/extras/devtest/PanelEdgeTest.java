package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.*;

import ee.extras.panel.PanelInletGeometry;
import ee.extras.panel.PanelPorts;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PanelEdgeTest {
    static int build(CommandSourceStack src) {
        var level = src.getLevel();
        int index = 0;
        for (var face : Direction.Plane.HORIZONTAL) {
            var pos = new BlockPos(3 + index++ * 5, -58, 24);
            DevTest.place(
                    level,
                    pos,
                    CEEBlocks.ELECTRICAL_PANEL
                            .getDefaultState()
                            .setValue(ElectricalPanelBlock.FACING, face));
            for (var slot : ElectricalPanelSlot.values()) {
                if (!slot.name().startsWith("QUARTER_")
                        || slot == ElectricalPanelSlot.QUARTER_CENTER) continue;
                var local = new Vec3(slot.center.x, slot.center.y, 0.875);
                var hit =
                        new BlockHitResult(
                                Vec3.atLowerCornerOf(pos)
                                        .add(
                                                VecHelper.rotateCentered(
                                                        local,
                                                        180 - face.toYRot(),
                                                        Direction.Axis.Y)),
                                face,
                                pos,
                                false);
                var stack = new ItemStack(CEEBlocks.DUPLEX_WIRE_TERMINATION.get(), 4);
                var player = CapTest.player(level, stack);
                if (!player.gameMode
                        .useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit)
                        .consumesAction()) throw new IllegalStateException("КРАЙ FAIL: установка");
            }
        }
        return check(src);
    }

    static int check(CommandSourceStack src) {
        var level = src.getLevel();
        int index = 0, count = 0;
        for (var face : Direction.Plane.HORIZONTAL) {
            var pos = new BlockPos(3 + index++ * 5, -58, 24);
            var panel = (ElectricalPanelBlockEntity) level.getBlockEntity(pos);
            var block = (ElectricalPanelBlock) level.getBlockState(pos).getBlock();
            for (var slot : ElectricalPanelSlot.values()) {
                if (!slot.name().startsWith("QUARTER_")
                        || slot == ElectricalPanelSlot.QUARTER_CENTER) continue;
                var module = panel.getAttachments()[slot.ordinal()];
                if (module == null) throw new IllegalStateException("КРАЙ FAIL: вставка пропала");
                boolean upper = PanelInletGeometry.upper(slot);
                for (int i = 0; i < 3; i++) {
                    var expected =
                            VecHelper.rotateCentered(
                                    PanelInletGeometry.node(slot, i),
                                    180 - face.toYRot(),
                                    Direction.Axis.Y);
                    var a =
                            block.getNodePosition(
                                    level, pos, level.getBlockState(pos), module.nodes[i].id());
                    var b = panel.getNodePositions().get(module.nodes[i].id());
                    if (a.distanceTo(expected) > 1e-6 || b.distanceTo(expected) > 1e-6)
                        throw new IllegalStateException("КРАЙ FAIL: положения узлов расходятся");
                }
                var inlet =
                        VecHelper.rotateCentered(
                                PanelInletGeometry.node(slot, 2),
                                180 - face.toYRot(),
                                Direction.Axis.Y);
                var edgeHit =
                        new BlockHitResult(
                                Vec3.atLowerCornerOf(pos).add(inlet),
                                upper ? Direction.UP : Direction.DOWN,
                                pos,
                                false);
                var cable = PanelPorts.hit(level, edgeHit);
                if (cable == null || !cable.cable().equals(module.nodes[2]))
                    throw new IllegalStateException("КРАЙ FAIL: ввод снаружи не выбирается");
                var internal = PanelInletGeometry.node(slot, 0);
                if (upper ? internal.y >= inlet.y : internal.y <= inlet.y)
                    throw new IllegalStateException("КРАЙ FAIL: клеммы не внутрь");
                if (block.isNodeAccessible(
                        level, pos, level.getBlockState(pos), module.nodes[2].id()))
                    throw new IllegalStateException(
                            "КРАЙ FAIL: оболочка доступна обычному проводу");
                count++;
            }
        }
        src.sendSuccess(
                () ->
                        Component.literal(
                                "КРАЙ PASS: 16 вводов, 4 направления щитка, кабель сверху/снизу,"
                                    + " две клеммы внутри, номера узлов сохранены"),
                false);
        return 1;
    }

    private PanelEdgeTest() {}
}
