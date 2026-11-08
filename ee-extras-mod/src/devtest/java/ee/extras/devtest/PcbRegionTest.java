package ee.extras.devtest;

import ee.extras.pcb.*;

/** Проверка настоящей атомарной операции и серверного меню. */
public final class PcbRegionTest {
    private PcbRegionTest() {}
    private static <T> T call(String owner, String method, Object... args) {
        try { return PcbModulesTest.call(Class.forName("ee.extras.pcb." + owner), null, method, args); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }
    private static boolean apply(PcbEditorMenu menu, PcbEditorPackets.Region packet) {
        return PcbModulesTest.call(PcbEditorMenu.class, menu, "applyRegion", packet);
    }
    private static void check(boolean value, String text) {
        if (!value) throw new IllegalStateException("Участок платы: " + text);
    }

    public static void run(PcbEditorMenu menu, PcbEditorBlockEntity editor, net.minecraft.world.entity.player.Player player) {
        var source = PcbTopology.create(2);
        int definition = call("PcbWiring", "mirror", (int) call("PcbWiring", "component", 25, 0, 5, 0, false));
        PcbTopology.set(source, 0, 85, PcbTopology.COMPONENT);
        call("PcbTopology", "setPart", source, 0, 85, definition);
        PcbTopology.set(source, 1, 85, PcbTopology.TRACE);
        var parameters = new net.minecraft.nbt.CompoundTag();
        // Для резистора проверяется произвольный числовой номинал, для регистра — отражение и 11 выводов.
        PcbTopology.set(source, 0, 117, PcbTopology.COMPONENT);
        call("PcbTopology", "setPart", source, 0, 117, (int) call("PcbWiring", "component", 0, 0, 5, 0, false));
        parameters.putDouble("Nominal", 733.25);
        call("PcbParameters", "put", source, 117, parameters);
        call("PcbTopology", "setEdgeCut", source, 1, 85, 0, true);
        call("PcbTopology", "toggleCut", source, 118);
        check(!(boolean) call("PcbRegion", "selection", source, 85, 86), "нельзя обрезать корпус рамкой");
        long id = call("PcbModuleIds", "get", source, 85);
        net.minecraft.nbt.CompoundTag pasted = call("PcbRegion", "apply", source, source, 85, 119, 153, false);
        check(pasted != null, "копирование группы в свободное место");
        check(pasted.getIntArray("ElectricalParts")[153] == definition, "копируется отражение");
        check((long) call("PcbModuleIds", "get", pasted, 153) != id && (long) call("PcbModuleIds", "get", pasted, 85) == id, "копия получает новую идентичность");
        check(((net.minecraft.nbt.CompoundTag) call("PcbParameters", "get", pasted, 185)).getDouble("Nominal") == 733.25, "копируется произвольный номинал");
        check(PcbTopology.get(pasted, 1, 153) == PcbTopology.TRACE && (boolean) call("PcbTopology", "cut", pasted, 186), "все слои и контур копируются");
        check((boolean) call("PcbTopology", "edgeCut", pasted, 1, 153, 0) && (boolean) call("PcbTopology", "edgeCut", pasted, 1, 152, 1), "разрыв границы симметричен");
        check(call("PcbRegion", "apply", source, source, 85, 119, 85, false) == null, "копия не затирает исходник");
        check(call("PcbRegion", "apply", source, source, 85, 119, 255, false) == null, "край платы");
        net.minecraft.nbt.CompoundTag moved = call("PcbRegion", "apply", source, source, 85, 119, 86, true);
        check(moved != null && (long) call("PcbModuleIds", "get", moved, 86) == id && moved.getIntArray("ElectricalParts")[85] == 0, "перекрывающийся перенос сохраняет идентичность");
        var changed = source.copy(); PcbTopology.set(changed, 1, 0, PcbTopology.TRACE);
        check(call("PcbRegion", "apply", changed, source, 85, 119, 153, true) == null, "устаревший перенос отклонён");
        PcbModulesTest.call(PcbEditorBlockEntity.class, editor, "replaceTopology", source);
        check(apply(menu, new PcbEditorPackets.Region(menu.containerId, 0, 85, 119, 0)), "серверный буфер принимает рамку");
        check(apply(menu, new PcbEditorPackets.Region(menu.containerId, 2, 0, 0, 153)), "сервер вставляет буфер");
        check(menu.clickMenuButton(player, PcbEditorMenu.UNDO), "группа отменяется одним шагом");
        check(editor.topology().getIntArray("ElectricalParts")[153] == 0 && editor.topology().getIntArray("ElectricalParts")[85] == definition, "отмена возвращает исходный участок");
        check(menu.clickMenuButton(player, PcbEditorMenu.REDO), "повтор группы");
        check(editor.topology().getIntArray("ElectricalParts")[153] == definition, "повтор восстанавливает целую группу");
    }
}
