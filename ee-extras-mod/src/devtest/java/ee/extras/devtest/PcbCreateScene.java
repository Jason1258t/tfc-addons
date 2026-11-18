package ee.extras.devtest;

import tfc.stand.scene.Scene;

/** Клиентская демонстрация реальной платы, независимых табло и лампы. */
final class PcbCreateScene {
    static final boolean ON = Scene.requested("pcb-create");

    static void start() {
        var scene = new Scene("pcb-create").area(16, 10);
        scene.server("плата, два табло и лампа", context -> {
            PcbCreateTest.origin(context.at(0, 1, 0));
            PcbCreateTest.build(context.player().createCommandSourceStack());
        });
        scene.ticks(100);
        scene.server("число, состояние и физическая лампа", context -> {
            PcbCreateTest.check(context.player().createCommandSourceStack(), 2.5);
            PcbCreateTest.displays(context.player().createCommandSourceStack());
            PcbCreateTest.state(context.player().createCommandSourceStack(), true);
        });
        scene.gui(false).camera(0, 1.2, 0, 4, 180, 65).shot("adapter");
        scene.camera(0, 1.5, -5, 5, 180, 20).shot("voltage");
        scene.camera(0, 1.5, 5, 5, 180, 20).shot("state");
        scene.camera(-1, 1.4, 0, 3, 100, 20).shot("lamp-on");
        scene.server("отключение питания", context -> PcbCreateTest.power(context.level(), PcbCreateTest.SUPPLY, 0));
        scene.ticks(80);
        scene.server("приёмники погасли", context -> {
            PcbCreateTest.blank(context.player().createCommandSourceStack());
            PcbCreateTest.state(context.player().createCommandSourceStack(), false);
        });
        scene.shot("lamp-off");
        scene.server("восстановление питания", context -> PcbCreateTest.power(context.level(), PcbCreateTest.SUPPLY, 5));
        scene.ticks(80);
        scene.server("приёмники восстановлены", context -> {
            PcbCreateTest.displays(context.player().createCommandSourceStack());
            PcbCreateTest.state(context.player().createCommandSourceStack(), true);
        });
        scene.camera(1, 1.5, 0, 13, 150, 40).shot("complete").start();
    }
}
