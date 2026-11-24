package ee.extras.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;

import net.minecraft.world.phys.Vec3;

/** Общая геометрия модели и клемм: кабель снаружи щита, отдельные провода внутри. */
public final class PanelInletGeometry {
    /** У EE FLIP по умолчанию true: фиксируем вариант, для которого рассчитаны координаты. */
    public static net.minecraft.world.level.block.state.BlockState modelState() {
        return com.george_vi.electroenergetics.CEEBlocks.DUPLEX_WIRE_TERMINATION
                .getDefaultState()
                .setValue(
                        com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock
                                .FACING,
                        net.minecraft.core.Direction.UP)
                .setValue(
                        com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock
                                .ROLL,
                        false)
                .setValue(
                        com.george_vi.electroenergetics.content.bundled_wire
                                .BundledWireTerminationBlock.FLIP,
                        false);
    }

    /** То же преобразование, что у PoseStack модели; используется проверкой её baked-вершин. */
    public static Vec3 modelPoint(ElectricalPanelSlot slot, Vec3 point) {
        var anchor = anchor(slot);
        double x = (point.x - 0.5) * 0.5, y = (point.z - 9.5 / 16) * 0.5, z = -point.y * 0.5;
        return anchor.add(upper(slot) ? x : -x, upper(slot) ? y : -y, z);
    }

    public static boolean upper(ElectricalPanelSlot slot) {
        return slot.center.y > 0.5;
    }

    public static Vec3 anchor(ElectricalPanelSlot slot) {
        return new Vec3(slot.center.x, (upper(slot) ? 14.75 : 1.25) / 16, 15.9 / 16);
    }

    public static Vec3 node(ElectricalPanelSlot slot, int index) {
        var anchor = anchor(slot);
        if (index == 2) return new Vec3(anchor.x, upper(slot) ? 17.0 / 16 : -1.0 / 16, 14.9 / 16);
        return new Vec3(
                anchor.x + (index == 0 ? -1.125 : 1.125) / 16,
                anchor.y + (upper(slot) ? -0.25 : 0.25) / 16,
                14.9 / 16);
    }

    private PanelInletGeometry() {}
}
