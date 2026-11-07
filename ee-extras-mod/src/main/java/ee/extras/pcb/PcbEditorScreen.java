package ee.extras.pcb;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Русский экран редактора: клик по ячейке применяет выбранный инструмент на текущем слое. */
public class PcbEditorScreen extends AbstractContainerScreen<PcbEditorMenu> {
    private int palettePage;
    private int lastSelected = -1;
    private static final int GRID_X = 12;
    private static final int GRID_Y = 26;
    private static final int CELL = 9;
    private static final String[] PARTS = java.util.Arrays.stream(PcbModules.ALL).map(PcbModules.Module::name).toArray(String[]::new);
    private static final String[] TOOLS = {"Трасса", "Метка", "Переход", "Вывод", "Ластик", "Деталь", "Контур", "Перенос"};
    private static final String[] TOOL_HINTS = {
            "Провести дорожку на текущем слое", "Метка компонента для пассивного чертежа",
            "Соединить дорожки на разных слоях", "Добавить контакт на краю первого слоя",
            "Очистить ячейку текущего слоя", "Поставить выбранную электрическую деталь",
            "Вырезать ячейку платы или вернуть материал",
            "Выбрать модуль и указать новое место; ПКМ — выбрать и настроить"
    };
    private static final int TOOL_X = 164;
    private static final int TOOL_Y = 27;
    private static final int TOOL_WIDTH = 42;
    private static final int TOOL_HEIGHT = 15;
    private static final int PALETTE_X = 199;
    private static final int PALETTE_Y = 153;
    private static final int PALETTE_CELL = 11;
    private static final int PALETTE_COLUMNS = 8;
    private static final int SETTINGS_Y = 181;
    private static final int SETTINGS_ROW = 15;
    private static final int[] PART_BUTTONS = {PcbEditorMenu.ROTATE, PcbEditorMenu.OHMS, PcbEditorMenu.VOLTS};
    private static final int[] BUTTON_Y = {73, 86, 99, 112, 125, 138};
    private static final int[] BUTTON_ID = {PcbEditorMenu.LAYER, PcbEditorMenu.LEVEL,
            PcbEditorMenu.CHECK, PcbEditorMenu.SAVE, PcbEditorMenu.LOAD, PcbEditorMenu.CLEAR};

    public PcbEditorScreen(PcbEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 304;
        imageHeight = 250;
        inventoryLabelY = 10000;
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        int tool = toolAt(localX, localY);
        if (tool >= 0) {
            click(PcbEditorMenu.SELECT_TOOL + tool);
            return true;
        }
        if (localX >= TOOL_X && localX < TOOL_X + 126) {
            for (int i = 0; i < BUTTON_Y.length; i++) {
                if (localY >= BUTTON_Y[i] && localY < BUTTON_Y[i] + 13) {
                    click(BUTTON_ID[i]);
                    return true;
                }
            }
        }
        if (localX >= TOOL_X && localX < PALETTE_X - 3 && localY >= PALETTE_Y && localY < PALETTE_Y + 22) {
            palettePage = Math.floorMod(palettePage + (button == 1 ? -1 : 1), pageCount());
            return true;
        }
        int part = partAt(localX, localY);
        if (part >= 0) {
            if (button == 0 && menu.available(part)) click(PcbEditorMenu.SELECT_PART + part);
            return true;
        }
        if (localX >= PALETTE_X && localX < TOOL_X + 126 && localY >= SETTINGS_Y
                && localY < SETTINGS_Y + SETTINGS_ROW * PART_BUTTONS.length) {
            int action = PART_BUTTONS[(int) (localY - SETTINGS_Y) / SETTINGS_ROW];
            if (action == PcbEditorMenu.ROTATE && button == 1
                    && PcbWiring.KINDS[menu.part()] == PcbCircuitDevice.Kind.RELAY) action = PcbEditorMenu.CONTACT;
            click(action);
            return true;
        }
        int x = (int) Math.floor((localX - GRID_X) / CELL);
        int y = (int) Math.floor((localY - GRID_Y) / CELL);
        if (x >= 0 && y >= 0 && x < PcbTopology.SIZE && y < PcbTopology.SIZE) {
            if (button == 0) click(y * PcbTopology.SIZE + x);
            else if (button == 1) click(PcbEditorMenu.PICK + y * PcbTopology.SIZE + x);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int partAt(double x, double y) {
        if (x < PALETTE_X || x >= PALETTE_X + PALETTE_CELL * PALETTE_COLUMNS
                || y < PALETTE_Y || y >= PALETTE_Y + 2 * PALETTE_CELL) return -1;
        int part = (int) ((y - PALETTE_Y) / PALETTE_CELL) * PALETTE_COLUMNS
                + (int) ((x - PALETTE_X) / PALETTE_CELL) + palettePage * 16;
        return part < PARTS.length ? part : -1;
    }

    private static int pageCount() { return (PARTS.length + 15) / 16; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= TOOL_X && x < TOOL_X + 126 && y >= PALETTE_Y && y < PALETTE_Y + 22) {
            palettePage = Math.floorMod(palettePage + (vertical < 0 ? 1 : -1), pageCount());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private static int toolAt(double x, double y) {
        if (x < TOOL_X || x >= TOOL_X + 3 * TOOL_WIDTH || y < TOOL_Y || y >= TOOL_Y + 3 * TOOL_HEIGHT) return -1;
        int tool = (int) ((y - TOOL_Y) / TOOL_HEIGHT) * 3 + (int) ((x - TOOL_X) / TOOL_WIDTH);
        return tool < TOOLS.length ? tool : -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (lastSelected != menu.part()) {
            lastSelected = menu.part();
            palettePage = menu.part() / 16;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int tool = toolAt(mouseX - leftPos, mouseY - topPos);
        if (tool >= 0) {
            graphics.renderTooltip(font, Component.literal(TOOL_HINTS[tool]), mouseX, mouseY);
            return;
        }
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        if (localX >= TOOL_X && localX < PALETTE_X - 3 && localY >= PALETTE_Y && localY < PALETTE_Y + 22) {
            graphics.renderTooltip(font, Component.literal("Каталог: ЛКМ вперёд, ПКМ назад; колесо — прокрутка"), mouseX, mouseY);
            return;
        }
        int part = partAt(localX, localY);
        if (part >= 0) {
            int definition = PcbWiring.component(part, 0, 0, 0, false);
            String label = PARTS[part] + " · " + PcbFootprint.width(definition) + "×" + PcbFootprint.height(definition);
            if (!menu.available(part)) label += " · с L" + PcbWiring.minimumLevel(part);
            graphics.renderTooltip(font, Component.literal(label), mouseX, mouseY);
            return;
        }
        if (localX >= TOOL_X && localX < TOOL_X + 126 && localY >= 229 && localY < 242) {
            graphics.renderTooltip(font, Component.literal(menu.statusText()), mouseX, mouseY);
            return;
        }
        if (localX >= 268 && localX < 284 && localY >= 8 && localY < 24 && !menu.getSlot(0).hasItem()) {
            graphics.renderTooltip(font, Component.literal("Бумага или чертёж. Чистую плату можно оставить в инвентаре."),
                    mouseX, mouseY);
            return;
        }
        int x = localX - GRID_X;
        int y = mouseY - topPos - GRID_Y;
        if (x < 0 || y < 0 || x >= CELL * PcbTopology.SIZE || y >= CELL * PcbTopology.SIZE
                || menu.editor() == null) return;
        CompoundTag topology = menu.editor().topology();
        int cell = y / CELL * PcbTopology.SIZE + x / CELL;
        if (menu.layer() == 0 && PcbTopology.get(topology, 0, cell) == PcbTopology.TERMINAL) {
            int number = 1;
            for (int i = 0; i < cell; i++) if (PcbTopology.get(topology, 0, i) == PcbTopology.TERMINAL) number++;
            graphics.renderTooltip(font, Component.translatable("ee_extras.pcb.terminal", number), mouseX, mouseY);
            return;
        }
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        int index = PcbFootprint.owner(definitions, menu.layer(), cell);
        if (index < 0) {
            int start = menu.layer() * PcbTopology.CELLS;
            for (int anchor = start; anchor < Math.min(start + PcbTopology.CELLS, definitions.length); anchor++) {
                int definition = definitions[anchor];
                if (!PcbFootprint.valid(definition)) continue;
                var module = PcbModules.get(definition);
                for (int pin = 0; pin < module.pins(); pin++) {
                    if (PcbWiring.pinCell(anchor, definition, pin) != start + cell) continue;
                    graphics.renderTooltip(font, Component.literal(module.name() + " · " + module.pinName(pin)), mouseX, mouseY);
                    return;
                }
            }
            return;
        }
        int type = PcbWiring.type(definitions[index]);
        if (type >= PARTS.length) return;
        var item = PcbAssembly.item(PcbWiring.KINDS[type]);
        var component = new net.minecraft.world.item.ItemStack(item);
        graphics.renderTooltip(font, List.of(Component.literal(PARTS[type] + " · " + PcbFootprint.width(definitions[index]) + "×" + PcbFootprint.height(definitions[index])), component.getHoverName(), Component.literal("ПКМ: настроить · Перенос: переместить")), Optional.empty(),
                mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20252A);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF455057);
            graphics.fill(x, y, x + 16, y + 16, 0xFF11191D);
        }
        graphics.fill(leftPos + GRID_X - 2, topPos + GRID_Y - 2, leftPos + GRID_X + CELL * PcbTopology.SIZE + 2,
                topPos + GRID_Y + CELL * PcbTopology.SIZE + 2, 0xFF0B1114);
        PcbEditorBlockEntity editor = menu.editor();
        CompoundTag topology = editor == null ? PcbTopology.create(menu.level()) : editor.topology();
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        for (int y = 0; y < PcbTopology.SIZE; y++) {
            for (int x = 0; x < PcbTopology.SIZE; x++) {
                int cell = y * PcbTopology.SIZE + x;
                int value = PcbTopology.get(topology, menu.layer(), cell);
                int color = PcbTopology.cut(topology, cell) ? 0xFF0B1114 : switch (value) {
                    case PcbTopology.TRACE -> 0xFFD17B45;
                    case PcbTopology.COMPONENT -> 0xFFE3D36A;
                    case PcbTopology.VIA -> 0xFF69D4DB;
                    case PcbTopology.TERMINAL -> 0xFF79D878;
                    default -> (x + y) % 2 == 0 ? 0xFF153C32 : 0xFF12362D;
                };
                int sx = leftPos + GRID_X + x * CELL;
                int sy = topPos + GRID_Y + y * CELL;
                graphics.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, color);
            }
        }
        int start = menu.layer() * PcbTopology.CELLS;
        for (int index = start; index < Math.min(start + PcbTopology.CELLS, definitions.length); index++) {
            if (!PcbFootprint.valid(definitions[index])) continue;
            drawModule(graphics, definitions[index], index % PcbTopology.CELLS,
                    (menu.moving() == index % PcbTopology.CELLS || menu.selected() == index % PcbTopology.CELLS) ? 0xFFDFB75A : 0xFF456B62, false);
        }
        int hoverX = mouseX - leftPos - GRID_X;
        int hoverY = mouseY - topPos - GRID_Y;
        if (hoverX >= 0 && hoverY >= 0 && hoverX < CELL * PcbTopology.SIZE && hoverY < CELL * PcbTopology.SIZE) {
            int cell = hoverY / CELL * PcbTopology.SIZE + hoverX / CELL;
            int definition = menu.selectedDefinition();
            int ignored = -1;
            if (menu.tool() == PcbEditorMenu.MOVE_TOOL && menu.moving() >= 0) {
                ignored = start + menu.moving();
                definition = ignored < definitions.length ? definitions[ignored] : 0;
            }
            if ((menu.tool() == 5 || ignored >= 0) && PcbFootprint.valid(definition)) {
                boolean allowed = menu.available(PcbWiring.type(definition))
                        && PcbFootprint.canPlace(topology, menu.layer(), cell, definition, ignored);
                drawModule(graphics, definition, cell, allowed ? 0xAA65AB87 : 0xAAC9524A, true);
                int pins = PcbModules.get(definition).pins();
                for (int pin = 0; pin < pins; pin++) {
                    int contact = PcbWiring.pinCell(cell, definition, pin);
                    if (contact < 0) continue;
                    int sx = leftPos + GRID_X + contact % PcbTopology.SIZE * CELL;
                    int sy = topPos + GRID_Y + contact / PcbTopology.SIZE * CELL;
                    boolean connected = !PcbTopology.cut(topology, contact)
                            && PcbWiring.conductor(PcbTopology.get(topology, menu.layer(), contact));
                    graphics.renderOutline(sx, sy, CELL, CELL, connected ? 0xFF79D878 : 0xFFE57C72);
                }
            }
        }
        for (int part = palettePage * 16; part < Math.min(PARTS.length, palettePage * 16 + 16); part++) {
            int x = leftPos + PALETTE_X + (part - palettePage * 16) % PALETTE_COLUMNS * PALETTE_CELL;
            int y = topPos + PALETTE_Y + (part - palettePage * 16) / PALETTE_COLUMNS * PALETTE_CELL;
            graphics.fill(x, y, x + PALETTE_CELL - 1, y + PALETTE_CELL - 1,
                    part == menu.part() && menu.tool() == 5 ? 0xFF3E7863 : 0xFF46535A);
            drawPart(graphics, part, x, y, 0.625f);
            if (!menu.available(part)) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(x, y, x + PALETTE_CELL - 1, y + PALETTE_CELL - 1, 0xAA101416);
                graphics.pose().popPose();
            }
        }
        graphics.fill(leftPos + TOOL_X, topPos + PALETTE_Y, leftPos + PALETTE_X - 3, topPos + PALETTE_Y + 22, 0xFF46535A);
        for (int row = 0; row < PART_BUTTONS.length; row++) {
            int y = topPos + SETTINGS_Y + row * SETTINGS_ROW;
            boolean enabled = row == 0 || row == 1 && PcbModules.ALL[menu.part()].resistanceSetting()
                    || row == 2 && (PcbModules.ALL[menu.part()].thresholdSetting() || menu.getSlot(0).getItem().getItem() instanceof PcbSupplyItem);
            graphics.fill(leftPos + PALETTE_X, y, leftPos + TOOL_X + 126, y + SETTINGS_ROW - 1, enabled ? 0xFF66706C : 0xFF303C3A);
        }
        for (int tool = 0; tool < TOOLS.length; tool++) {
            int x = leftPos + TOOL_X + tool % 3 * TOOL_WIDTH;
            int y = topPos + TOOL_Y + tool / 3 * TOOL_HEIGHT;
            graphics.fill(x, y, x + TOOL_WIDTH - 1, y + TOOL_HEIGHT - 1,
                    tool == menu.tool() ? 0xFF3E7863 : 0xFF46535A);
        }
        for (int i = 0; i < BUTTON_Y.length; i++) {
            int y = BUTTON_Y[i];
            graphics.fill(leftPos + TOOL_X, topPos + y, leftPos + TOOL_X + 126, topPos + y + 13, 0xFF101416);
            graphics.fill(leftPos + TOOL_X + 1, topPos + y + 1, leftPos + TOOL_X + 125, topPos + y + 12, 0xFF66706C);
        }
    }

    private void drawModule(GuiGraphics graphics, int definition, int cell, int color, boolean ghost) {
        int x = leftPos + GRID_X + cell % PcbTopology.SIZE * CELL;
        int y = topPos + GRID_Y + cell / PcbTopology.SIZE * CELL;
        int w = PcbFootprint.width(definition) * CELL;
        int h = PcbFootprint.height(definition) * CELL;
        // Предпросмотр обрезается по рабочему полю, даже если корпус выходит за край.
        graphics.enableScissor(leftPos + GRID_X, topPos + GRID_Y,
                leftPos + GRID_X + CELL * PcbTopology.SIZE, topPos + GRID_Y + CELL * PcbTopology.SIZE);
        graphics.fill(x, y, x + w - 1, y + h - 1, color);
        graphics.renderOutline(x, y, w - 1, h - 1, ghost ? 0xFFE8ECCC : 0xFF9CBFB0);
        drawModel(graphics, definition, x, y, CELL);
        graphics.disableScissor();
    }

    private static void drawModel(GuiGraphics graphics, int definition, float x, float y, float cellSize) {
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 150);
        graphics.pose().scale(16 * cellSize, -16 * cellSize, 16 * cellSize);
        graphics.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        var vertices = graphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        PcbPartModels.render(definition, 0, 0, false, false, false, false, 50,
                graphics.pose(), vertices, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        graphics.pose().popPose();
        graphics.flush();
    }

    private static void drawPart(GuiGraphics graphics, int type, int x, int y, float scale) {
        if (type >= PARTS.length) return;
        int definition = PcbWiring.component(type, 0, 0, 0, false);
        float size = 16 * scale;
        float cellSize = size / Math.max(PcbFootprint.width(definition), PcbFootprint.height(definition));
        drawModel(graphics, definition, x + (size - PcbFootprint.width(definition) * cellSize) / 2,
                y + (size - PcbFootprint.height(definition) * cellSize) / 2, cellSize);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 8, 0xFFE7ECE8, false);
        graphics.drawString(font, "Инструменты", TOOL_X, 16, 0xFFD5DDD8, false);
        for (int tool = 0; tool < TOOLS.length; tool++) {
            int x = TOOL_X + tool % 3 * TOOL_WIDTH + TOOL_WIDTH / 2;
            int y = TOOL_Y + tool / 3 * TOOL_HEIGHT + 3;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y + 1, 0);
            graphics.pose().scale(0.85f, 0.85f, 1);
            graphics.drawCenteredString(font, TOOLS[tool], 0, 0, 0xFFFFFFFF);
            graphics.pose().popPose();
        }
        for (int i = 0; i < BUTTON_Y.length; i++) {
            Component label = switch (i) {
                case 0 -> Component.translatable("ee_extras.pcb.layer_value", menu.layer() + 1);
                case 1 -> Component.literal("Уровень: L" + menu.level());
                case 3 -> menu.getSlot(0).getItem().is(PcbItems.BURNT_BOARD.get())
                        ? Component.translatable("ee_extras.pcb.salvage")
                        : Component.translatable(!menu.getSlot(0).getItem().isEmpty() && PcbBoardItem.level(menu.getSlot(0).getItem()) == 0
                                ? "ee_extras.pcb.save" : "ee_extras.pcb.assemble");
                default -> Component.translatable("ee_extras.pcb." + new String[] {"layer", "level", "check", "save", "load", "clear"}[i]);
            };
            graphics.drawCenteredString(font, label, TOOL_X + 63, BUTTON_Y[i] + 2, 0xFFFFFFFF);
        }
        graphics.drawCenteredString(font, (palettePage + 1) + "/" + pageCount(), TOOL_X + 16, PALETTE_Y + 6, 0xFFFFFFFF);
        PcbCircuitDevice.Kind kind = PcbWiring.KINDS[menu.part()];
        double value = PcbWiring.OHMS[menu.resistance()];
        if (kind == PcbCircuitDevice.Kind.LAMP || kind == PcbCircuitDevice.Kind.LED) value = PcbCircuitDevice.indicatorResistance(value);
        ItemStack input = menu.getSlot(0).getItem();
        boolean supply = input.getItem() instanceof PcbSupplyItem;
        double limit = supply ? PcbSupplyBlock.VOLTS[PcbSupplyItem.setting(input)] : PcbWiring.VOLTS[menu.threshold()];
        String[] arrows = {"→", "↓", "←", "↑"};
        String orientation = arrows[menu.rotation()];
        if (kind == PcbCircuitDevice.Kind.RELAY) orientation += menu.normallyClosed() ? " · НЗ" : " · НО";
        String[] partLabels = {"Поворот " + orientation,
                kind == PcbCircuitDevice.Kind.CAPACITOR ? (int) value + " мФ" : (int) value + " Ом",
                supply ? (int) limit + " В на выходе" : kind == PcbCircuitDevice.Kind.FUSE || kind == PcbCircuitDevice.Kind.AMMETER
                        ? limit / 100 + " А" : (int) limit + " В"};
        if (PcbModules.ALL[menu.part()].timed()) {
            int ticks = PcbWiring.period(menu.part(), menu.resistance());
            partLabels[1] = ticks < 20 ? ticks + " тиков" : ticks / 20.0 + " с";
        }
        if (!PcbModules.ALL[menu.part()].resistanceSetting()) partLabels[1] = "Без номинала";
        if (!supply && !PcbModules.ALL[menu.part()].thresholdSetting())
            partLabels[2] = PcbModules.ALL[menu.part()].adjustable() ? "Положение в мире" : "Без порога";
        if (kind == PcbCircuitDevice.Kind.MECHANICAL_TIMER) partLabels[2] = "Shift: отмена";
        if (kind == PcbCircuitDevice.Kind.CLOCK) partLabels[2] = "Питание ≥ 3 В";
        for (int row = 0; row < partLabels.length; row++) {
            graphics.drawCenteredString(font, partLabels[row], PALETTE_X + 45, SETTINGS_Y + 3 + row * SETTINGS_ROW, 0xFFFFFFFF);
        }
        int color = menu.status() == 1 ? 0xFF79D878 : menu.status() >= 4 ? 0xFFE57C72 : 0xFFB6C0BA;
        String status = menu.status() == 0 ? "Проект не проверен" : menu.statusText();
        if (font.width(status) > 124) status = font.plainSubstrByWidth(status, 116) + "…";
        graphics.drawString(font, status, TOOL_X, 230, color, false);
    }

}
