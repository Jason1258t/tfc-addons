package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.backend.Backends;
import ee.extras.EeExtras;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import tfc.stand.scene.Scene;

/**
 * Провода EE через Flywheel на клиентском стенде (stand_submit scene=flywheel-wires): батарея, нагреватель и вольтметр,
 * медные и тонкие провода катушкой, в лог — backend Flywheel и замер WireDiag, кадр с Flywheel и тот же кадр базовым
 * рендером EE.
 */
final class FlywheelWireTest {
    static final boolean ON = Scene.requested("flywheel-wires");

    static void start() {
        Scene scene = new Scene("flywheel-wires");
        // Провода — настоящей катушкой от игрока: так EE шлёт их клиенту
        scene.server("приборы и провода", c -> {
            var bat = c.at(-4, 0, 2);
            var heater = c.at(4, 0, 2);
            var vm = c.at(0, 0, 6);
            c.set(bat, CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC, true));
            c.set(heater, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
            c.set(vm, CEEBlocks.VOLTMETER.getDefaultState());
            var p = c.player();
            boolean copper = DevTest.spoolWire(p, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16), bat, 0, heater, 0)
                    & DevTest.spoolWire(p, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16), bat, 1, heater, 1);
            boolean thin = DevTest.spoolWire(p, EeExtras.THIN_SPOOL.toStack(), heater, 0, vm, 0)
                    & DevTest.spoolWire(p, EeExtras.THIN_SPOOL.toStack(), heater, 1, vm, 1);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            scene.check("медные провода проложены катушкой", copper);
            scene.check("тонкие провода проложены катушкой", thin);
        });
        scene.until("провода у клиента", 400, () -> WireRenderer.getAllConnections().size() >= 4);
        scene.gui(false);
        scene.camera(0, 0.5, 4, 10, 180, 35);
        scene.client("backend Flywheel и замер", () -> {
            var backend = BackendManager.currentBackend();
            String name = backend == Backends.INDIRECT ? "indirect" : backend == Backends.INSTANCING ? "instancing"
                    : backend == BackendManager.offBackend() ? "off" : String.valueOf(backend);
            scene.note("backend Flywheel " + name + ", проводов у клиента " + WireRenderer.getAllConnections().size()
                    + ", GL " + com.mojang.blaze3d.platform.GlUtil.getRenderer());
            try {
                var report = ee.extras.WireDiag.class.getDeclaredMethod("report");
                report.setAccessible(true);
                report.invoke(null);
            } catch (ReflectiveOperationException e) {
                scene.check("замер WireDiag: " + e, false);
            }
        });
        scene.shot("flywheel");
        scene.client("базовый рендер EE", () -> {
            CEEConfigs.client().disableFlywheelWireRendering.set(true);
            WireRenderer.recreateVisuals();
        });
        scene.ticks(20).shot("basic");
        scene.client("снова Flywheel", () -> {
            CEEConfigs.client().disableFlywheelWireRendering.set(false);
            WireRenderer.recreateVisuals();
        });
        scene.ticks(20).shot("flywheel-again");
        scene.start();
    }

    private FlywheelWireTest() {
    }
}
