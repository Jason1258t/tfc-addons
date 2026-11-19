package ee.extras.devtest;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import ee.extras.pcb.PcbCircuitDevice;
import tfc.stand.scene.Scene;

/** Крупные клиентские кадры работающих модулей без падения игрока между снимками. */
final class PcbAcceptanceScene {
    static final boolean ON = Scene.requested("pcb-acceptance");

    static void start() {
        var scene = new Scene("pcb-acceptance").area(32, 40);
        scene.client("крупный план плат", () -> net.minecraft.client.Minecraft.getInstance().options.fov().set(35));
        scene.server("шесть физических плат с источниками и нагрузками", context -> {
            var source = context.player().createCommandSourceStack();
            for (int type = 36; type <= 38; type++) {
                PcbExtraTest.BOARD = context.at(0, 2, -20 + (type - 36) * 8);
                PcbExtraTest.build(source, type);
                if (type == 36) {
                    boolean adjusted = PcbModulesTest.call(PcbCircuitDevice.class, PcbExtraTest.device(context.level()),
                            "adjustComponent", 0, 25);
                    PcbExtraTest.check(adjusted, "ручка установлена на25%");
                }
                if (type == 38) PcbExtraTest.mask(context.level(), 111);
            }
            PcbSequentialTest.BOARD = context.at(0, 2, 4);
            PcbSequentialTest.SUPPLY = PcbSequentialTest.BOARD.east(4);
            PcbSequentialTest.build(source, 39);
            for (int input = 0; input < 8; input++)
                PcbModulesTest.call(PcbSequentialTest.class, null, "power", context.level(),
                        PcbSequentialTest.BOARD.offset(6, 0, input * 2), input < 2 ? 5 : 0);
            // Отдельный ряд: источники таймера не должны заменять входные батареи NAND.
            PcbTimersTest.buildAt(source, context.at(-8, 2, 12), false);
            PcbModulesTest.call(PcbTimersTest.class, null, "power", context.level(), context.at(-2, 2, 12), 24);
            PcbContactsTest.buildAt(source, context.at(-8, 2, 20), 21);
        }).ticks(140);
        scene.server("независимые электрические проверки перед снимками", context -> {
            var source = context.player().createCommandSourceStack();
            PcbExtraTest.BOARD = context.at(0, 2, -20);
            double volts = PcbExtraTest.snapshot(context.level()).getDouble("Meter");
            PcbExtraTest.check(Math.abs(volts - 3.15789) < .015, "нагруженная ручка: " + volts);
            PcbExtraTest.BOARD = context.at(0, 2, -12);
            PcbExtraTest.check(PcbExtraTest.snapshot(context.level()).getDouble("Watts") > .024,
                    "зуммер потребляет реальную мощность");
            var display = context.level().getBlockEntity(context.at(0, 2, -4));
            PcbExtraTest.check(display != null && display.getUpdateTag(context.level().registryAccess())
                    .getLong("LitIndicators") == 111, "семисегментный индикатор показывает маску111");
            PcbModulesTest.call(PcbSequentialTest.class, null, "expect", source, 14, true);
            PcbModulesTest.call(PcbTimersTest.class, null, "expect", source, 24.0);
        });
        String[] shots = {"knob", "buzzer", "seven-segment", "nand", "mechanical-timer"};
        for (int index = 0; index < shots.length; index++) {
            // Scene.camera уже отсчитывает X/Z от центра блока площадки.
            scene.gui(false).camera(index == 4 ? -8 : 0, 2.12, -20 + index * 8, 1.9, 180, 65);
            cleanShot(scene, shots[index]);
        }
        for (int position : new int[]{0, 50, 100}) {
            scene.server("трёхпозиционный переключатель: " + position, context -> {
                var device = DevicesSavedData.load(context.level()).getDevice(context.at(-8, 2, 20), PcbCircuitDevice.class);
                boolean adjusted = PcbModulesTest.call(PcbCircuitDevice.class, device, "adjustComponent", 0, position);
                PcbExtraTest.check(adjusted, "положение трёхпозиционного переключателя");
            }).ticks(40).server("контакт и нагрузка переключателя", context ->
                    PcbModulesTest.call(PcbContactsTest.class, null, "expect", context.player().createCommandSourceStack(),
                            position == 0 ? 24.0 : 0.0));
            scene.camera(-8, 2.12, 20, 1.9, 180, 65);
            cleanShot(scene, "selector-" + position);
        }
        scene.client("общий план всей оснастки", () -> net.minecraft.client.Minecraft.getInstance().options.fov().set(70));
        scene.camera(3, 2, 0, 30, 130, 60);
        cleanShot(scene, "overview");
        scene.start();
    }

    private static void cleanShot(Scene scene, String name) {
        scene.server("эффекты игрока не закрывают плату", context -> context.player().removeAllEffects());
        scene.client("убрать оставшиеся частицы перед кадром", () -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            client.player.removeAllEffects();
            client.particleEngine.iterateParticles(particle -> particle.remove());
        });
        scene.ticks(3).shot(name);
    }
}
