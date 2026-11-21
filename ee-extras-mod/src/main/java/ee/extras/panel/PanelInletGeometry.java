package ee.extras.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;

import net.minecraft.world.phys.Vec3;

/** Общая геометрия модели и клемм: кабель снаружи щита, отдельные провода внутри. */
public final class PanelInletGeometry {
    public static boolean upper(ElectricalPanelSlot slot) {
        return slot.center.y > 0.5;
    }

    public static Vec3 anchor(ElectricalPanelSlot slot) {
        return new Vec3(slot.center.x, (upper(slot) ? 14.75 : 1.25) / 16, 15.9 / 16);
    }

    public static Vec3 node(ElectricalPanelSlot slot, int index) {
        var anchor = anchor(slot);
        if (index == 2) return new Vec3(anchor.x, upper(slot) ? 1 : 0, 14.9 / 16);
        return new Vec3(
                anchor.x + (index == 0 ? -1.125 : 1.125) / 16,
                anchor.y + (upper(slot) ? -0.25 : 0.25) / 16,
                14.9 / 16);
    }

    private PanelInletGeometry() {}
}
