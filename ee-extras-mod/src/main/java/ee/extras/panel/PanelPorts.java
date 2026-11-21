package ee.extras.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;

import ee.extras.cable.CableEnd;
import ee.extras.cable.CableKind;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PanelPorts {
    public record Hit(CableEnd end, String label) {}

    public static CableEnd hit(Level level, BlockHitResult hit) {
        Hit port = identify(level, hit);
        return port == null ? null : port.end();
    }

    public static Hit identify(Level level, BlockHitResult hit) {
        if (!(level.getBlockEntity(hit.getBlockPos()) instanceof ElectricalPanelBlockEntity panel))
            return null;
        Direction facing = panel.getBlockState().getValue(ElectricalPanelBlock.FACING);
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        Vec3 canonical = VecHelper.rotateCentered(local, facing.toYRot() + 180.0, Direction.Axis.Y);
        // Верхняя и нижняя стенки доступны снаружи; спереди доступен и сам ввод у края.
        for (var attachment : panel.getAttachments()) {
            if (!(attachment instanceof PanelInlet)) continue;
            boolean upper = PanelInletGeometry.upper(attachment.slot);
            var anchor = PanelInletGeometry.anchor(attachment.slot);
            boolean edge = hit.getDirection() == (upper ? Direction.UP : Direction.DOWN);
            boolean front =
                    hit.getDirection() == facing
                            && (upper ? canonical.y >= 13.3 / 16 : canonical.y <= 2.7 / 16);
            if ((edge || front)
                    && Math.abs(canonical.x - anchor.x) <= 3.0 / 16
                    && canonical.z >= 10.5 / 16)
                return new Hit(end(attachment, 0), "panel_connector");
        }
        if (hit.getDirection() != facing) return null;
        var slot = panel.getHoveringAttachmentIndex(local);
        if (slot == null) return null;
        PanelAttachment module = panel.getAttachments()[slot.ordinal()];
        if (module instanceof PanelInlet) return new Hit(end(module, 0), "panel_connector");
        if (!(module instanceof PanelDuplexSwitch)) return null;

        Vec3 click = VecHelper.rotateCentered(local, facing.toYRot() + 180.0, Direction.Axis.Y);
        boolean second = slot.isHorizontal ? click.x > slot.center.x : click.y < slot.center.y;
        return new Hit(end(module, second ? 3 : 0), second ? "panel_output" : "panel_input");
    }

    /** Физическая точка узла; его номер и электрические связи остаются прежними. */
    public static Vec3 inletNode(
            com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity
                    panel,
            int id) {
        for (var attachment : panel.getAttachments()) {
            if (!(attachment instanceof PanelInlet)) continue;
            for (int i = 0; i < attachment.nodes.length; i++) {
                if (attachment.nodes[i].id() == id)
                    return VecHelper.rotateCentered(
                            PanelInletGeometry.node(attachment.slot, i),
                            180 - attachment.panelFacing.toYRot(),
                            Direction.Axis.Y);
            }
        }
        return null;
    }

    public static CableEnd resolve(Level level, InWorldNode selected) {
        if (!(level.getBlockEntity(selected.sourcePos())
                instanceof ElectricalPanelBlockEntity panel)) return null;
        for (PanelAttachment module : panel.getAttachments()) {
            if (!(module instanceof PanelInlet) && !(module instanceof PanelDuplexSwitch)) continue;
            for (int i = 0; i < module.nodes.length; i += 3) {
                if (module.nodes[i + 2].equals(selected)) return end(module, i);
            }
        }
        return null;
    }

    private static CableEnd end(PanelAttachment module, int offset) {
        return new CableEnd(
                module.pos,
                CableKind.DUPLEX,
                false,
                module.nodes[offset + 2].id(),
                new int[] {module.nodes[offset].id(), module.nodes[offset + 1].id()});
    }

    private PanelPorts() {}
}
