package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import tfc.stand.scene.Scene;

/** Четыре четвертных ввода на каждой стороне щитка: размер и парные клеммы внутрь. */
final class PanelInletScene {
    static void start() {
        var scene = new Scene("panel-inlets");
        var faces =
                new Direction[] {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        scene.server(
                "установить соединители во все четверти четырёх щитков",
                c -> {
                    for (int i = 0; i < faces.length; i++) {
                        var face = faces[i];
                        var pos = c.at(i * 3 - 4, 2, 0);
                        c.set(pos.relative(face.getOpposite()), Blocks.STONE.defaultBlockState());
                        var state =
                                CEEBlocks.ELECTRICAL_PANEL
                                        .getDefaultState()
                                        .setValue(ElectricalPanelBlock.FACING, face);
                        c.set(pos, state);
                        for (var slot : ElectricalPanelSlot.values()) {
                            if (!slot.name().startsWith("QUARTER_")
                                    || slot == ElectricalPanelSlot.QUARTER_CENTER) continue;
                            var local = new Vec3(slot.center.x, slot.center.y, 0.9375);
                            var point =
                                    VecHelper.rotateCentered(
                                                    local, -face.toYRot() + 180, Direction.Axis.Y)
                                            .add(Vec3.atLowerCornerOf(pos));
                            var item = new ItemStack(CEEBlocks.DUPLEX_WIRE_TERMINATION.get(), 64);
                            c.player().setItemInHand(InteractionHand.MAIN_HAND, item);
                            state.useItemOn(
                                    item,
                                    c.level(),
                                    c.player(),
                                    InteractionHand.MAIN_HAND,
                                    new BlockHitResult(point, face, pos, false));
                        }
                        var panel = (ElectricalPanelBlockEntity) c.level().getBlockEntity(pos);
                        int count = 0;
                        for (var module : panel.getAttachments()) if (module != null) count++;
                        scene.check("четыре ввода на стороне " + face, count == 4);
                        for (var slot :
                                new ElectricalPanelSlot[] {
                                    ElectricalPanelSlot.QUARTER_LEFT_UPPER,
                                    ElectricalPanelSlot.QUARTER_RIGHT_UPPER
                                }) {
                            var upper = panel.getAttachments()[slot.ordinal()];
                            var lowerSlot =
                                    slot == ElectricalPanelSlot.QUARTER_LEFT_UPPER
                                            ? ElectricalPanelSlot.QUARTER_LEFT_LOWER
                                            : ElectricalPanelSlot.QUARTER_RIGHT_LOWER;
                            var lower = panel.getAttachments()[lowerSlot.ordinal()];
                            for (int conductor = 0; conductor < 2; conductor++)
                                DevTest.wire(
                                        c.level(),
                                        pos,
                                        upper.nodes[conductor].id(),
                                        pos,
                                        lower.nodes[conductor].id());
                            for (var module :
                                    new com.george_vi.electroenergetics.content.electrical_panel
                                                    .attachments.PanelAttachment[] {upper, lower}) {
                                boolean top = ee.extras.panel.PanelInletGeometry.upper(module.slot);
                                var outside =
                                        pos.relative(
                                                        face.getClockWise(),
                                                        slot.center.x > 0.5 ? 1 : -1)
                                                .offset(0, top ? 2 : -2, 0);
                                c.set(
                                        outside,
                                        CEEBlocks.DUPLEX_WIRE_TERMINATION
                                                .getDefaultState()
                                                .setValue(
                                                        net.minecraft.world.level.block.state
                                                                .properties.BlockStateProperties
                                                                .FACING,
                                                        top ? Direction.DOWN : Direction.UP));
                                var endpoint =
                                        ee.extras.cable.CableEnd.of(
                                                c.level(),
                                                outside,
                                                c.level().getBlockState(outside));
                                var inlet =
                                        ee.extras.panel.PanelPorts.resolve(
                                                c.level(), module.nodes[2]);
                                scene.check(
                                        "кабель снаружи " + face + "/" + module.slot,
                                        ee.extras.cable.CableLaying.connect(
                                                c.level(),
                                                ee.extras.cable.CableKind.DUPLEX,
                                                endpoint,
                                                inlet));
                            }
                        }
                        panel.sendData();
                    }
                    c.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                });
        scene.gui(false);
        for (int i = 0; i < faces.length; i++) {
            final int index = i;
            var face = faces[i];
            scene.camera(i * 3 - 4, 2.5, 0, 4.2, face.toYRot() + 180, 0);
            scene.until(
                    "четыре ввода получены клиентом: " + face,
                    400,
                    () -> {
                        var be =
                                Minecraft.getInstance()
                                        .level
                                        .getBlockEntity(scene.origin().offset(index * 3 - 4, 2, 0));
                        if (!(be instanceof ElectricalPanelBlockEntity panel)) return false;
                        int count = 0;
                        for (var module : panel.getAttachments()) if (module != null) count++;
                        return count == 4;
                    });
            scene.ticks(30).shot("inlets-" + face.getName());
        }
        scene.start();
    }

    private PanelInletScene() {}
}
