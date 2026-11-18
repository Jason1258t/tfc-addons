package ee.extras.devtest;

import tfc.stand.scene.Scene;

/** Клиентская демонстрация реальной платы, независимых табло и лампы. */
final class PcbCreateScene {
    static final boolean ON = Scene.requested("pcb-create");

    private static net.minecraft.core.BlockPos editor;
    private static java.awt.Robot robot;

    private static java.awt.Robot robot() {
        try {
            if (robot == null) robot = new java.awt.Robot();
            return robot;
        } catch (java.awt.AWTException error) {
            throw new IllegalStateException("Оконный ввод недоступен", error);
        }
    }

    private static void key(int code, boolean control) {
        var input = robot();
        if (control) input.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
        input.keyPress(code);
        input.keyRelease(code);
        if (control) input.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
    }

    private static void click(int x, int y) {
        var input = robot();
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        input.mouseMove(window.getX() + (int) Math.round(x * window.getGuiScale()),
                window.getY() + (int) Math.round(y * window.getGuiScale()));
        input.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        input.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
    }

    private static Object field(Object object, String name) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void checkPart(Scene.Ctx context, int expected) {
        var table = (ee.extras.pcb.PcbEditorBlockEntity) context.level().getBlockEntity(editor);
        int[] parts = table.topology().getIntArray("ElectricalParts");
        int definition = parts.length > 85 ? parts[85] : 0;
        int actual = definition == 0 ? -1 : PcbModulesTest.call(PcbBridgeTest.wiring(), null, "type", definition);
        PcbExtraTest.check(actual == expected, "нативный монтаж адаптера: " + actual + " вместо " + expected);
    }

    static void start() {
        var scene = new Scene("pcb-create").area(16, 10);
        scene.client("масштаб интерфейса", () -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            client.options.guiScale().set(2);
            client.resizeDisplay();
        });
        scene.server("плата, два табло и лампа", context -> {
            PcbCreateTest.origin(context.at(0, 1, 0));
            PcbCreateTest.build(context.player().createCommandSourceStack());
            var gallery = context.at(-3, 1, 3);
            DevTest.place(context.level(), gallery, ee.extras.pcb.PcbItems.CIRCUIT_BLOCK.get().defaultBlockState()
                    .setValue(ee.extras.pcb.PcbCircuitBlock.CUSTOM, true));
            net.minecraft.world.item.ItemStack board = PcbModulesTest.call(ee.extras.pcb.PcbCircuitBlockEntity.class,
                    context.level().getBlockEntity(PcbCreateTest.BOARD), "board");
            PcbModulesTest.call(ee.extras.pcb.PcbCircuitBlockEntity.class, context.level().getBlockEntity(gallery),
                    "setBoard", board);
        });
        scene.ticks(100);
        scene.server("число, состояние и физическая лампа", context -> {
            PcbCreateTest.check(context.player().createCommandSourceStack(), 2.5);
            PcbCreateTest.displays(context.player().createCommandSourceStack());
            PcbCreateTest.state(context.player().createCommandSourceStack(), true);
        });
        scene.gui(false).camera(-3, 1.1, 3, 1.8, 180, 72).shot("adapter");
        scene.camera(1, 1.5, -5, 4, 180, 10).shot("voltage");
        scene.camera(1, 1.5, -8, 4, 0, 10).shot("second-adapter");
        scene.camera(-7, 1.5, 0, 3.4, 0, 10).shot("flap-display");
        scene.camera(1, 1.5, 5, 4, 180, 10).shot("state");
        scene.camera(-1, 1.4, 0, 2.4, -90, 15).shot("lamp-on");
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
        scene.camera(1, 1.5, 0, 13, 150, 40).shot("complete");
        scene.gui(true).server("стол для монтажа адаптера", context -> {
            editor = PcbCreateTest.BOARD.south(9);
            var standing = editor.south(2);
            DevTest.place(context.level(), standing.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            DevTest.place(context.level(), editor, ee.extras.pcb.PcbItems.EDITOR_BLOCK.get().defaultBlockState());
            var table = (ee.extras.pcb.PcbEditorBlockEntity) context.level().getBlockEntity(editor);
            var design = ee.extras.pcb.PcbItems.PCB_DESIGN.toStack();
            ee.extras.pcb.PcbTopology.attach(design, ee.extras.pcb.PcbTopology.create(2));
            table.setItem(0, design);
            table.loadDesign();
            context.player().teleportTo(context.level(), standing.getX() + .5, standing.getY(), standing.getZ() + .5, 180, 20);
            table.open(context.player());
        });
        scene.until("редактор открыт", 100, () -> net.minecraft.client.Minecraft.getInstance().screen instanceof ee.extras.pcb.PcbEditorScreen);
        scene.client("каталог клавишей K", () -> key(java.awt.event.KeyEvent.VK_K, false));
        scene.ticks(15);
        scene.client("поиск адаптера", () -> {
            var screen = net.minecraft.client.Minecraft.getInstance().screen;
            var search = (net.minecraft.client.gui.components.EditBox) field(screen, "catalogSearch");
            search.setValue("Адаптер Create");
        });
        scene.ticks(10).shot("editor-catalog");
        scene.client("выбор карточки мышью", () -> click(145, 70));
        scene.ticks(15);
        scene.client("монтаж адаптера мышью", () -> {
            var screen = (ee.extras.pcb.PcbEditorScreen) net.minecraft.client.Minecraft.getInstance().screen;
            click(screen.cellScreenX(5), screen.cellScreenY(5));
        });
        scene.ticks(20);
        scene.server("проверка установленного модуля", context -> checkPart(context, 40));
        scene.shot("editor-mounted");
        scene.client("отмена монтажа", () -> key(java.awt.event.KeyEvent.VK_Z, true));
        scene.ticks(20);
        scene.server("отмена принята", context -> checkPart(context, -1));
        scene.client("повтор кнопкой", () -> {
            var screen = net.minecraft.client.Minecraft.getInstance().screen;
            click(screen.width - 169, 18);
        });
        scene.ticks(20);
        scene.server("повтор принят", context -> checkPart(context, 40));
        scene.shot("editor-redo").start();
    }
}
