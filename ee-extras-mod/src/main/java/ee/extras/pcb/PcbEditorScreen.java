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
    private net.minecraft.client.gui.components.EditBox nominalInput;
    private net.minecraft.client.gui.components.EditBox thresholdInput;
    private boolean numbersDirty;
    private boolean refreshingNumbers;
    private int numbersAnchor = -2;
    private int numbersDefinition;
    private long numbersInstance;
    private boolean catalogOpen;
    private boolean bomOpen;
    private boolean diagnosticsOpen;
    private int diagnosticScroll;
    private java.util.List<PcbDiagnostics.Issue> diagnostics = java.util.List.of();
    private int bomScroll;
    private boolean panning;
    private net.minecraft.client.gui.components.EditBox catalogSearch;
    private String catalogSection = "Все";
    private int catalogScroll;
    private final java.util.ArrayList<Integer> recent = new java.util.ArrayList<>();
    private static final String[] SECTIONS = java.util.stream.Stream.concat(java.util.stream.Stream.of("Все"),
            java.util.Arrays.stream(PcbModules.ALL).map(PcbModules.Module::section).distinct()).toArray(String[]::new);
    private boolean sidePalette;
    private int canvasLeft = 12;
    private final boolean[] highlighted = new boolean[PcbTopology.CELLS * PcbTopology.MAX_LAYERS];
    private int canvasRight = 156;
    private int canvasBottom = 170;
    private boolean insideCanvas(double x, double y) { return x >= canvasLeft && x < canvasRight && y >= 26 && y < canvasBottom; }
    public int canvasCenterX() { return leftPos + (canvasLeft + canvasRight) / 2; }
    public int canvasCenterY() { return topPos + (26 + canvasBottom) / 2; }
    public int cellScreenX(int x) { return leftPos + GRID_X + x * CELL + CELL / 2; }
    public int cellScreenY(int y) { return topPos + GRID_Y + y * CELL + CELL / 2; }
    private void fitCanvas() {
        CELL = Math.clamp(Math.min((canvasRight - canvasLeft) / 16, (canvasBottom - 26) / 16), 5, 25);
        GRID_X = canvasLeft + (canvasRight - canvasLeft - CELL * 16) / 2;
        GRID_Y = 26 + (canvasBottom - 26 - CELL * 16) / 2;
    }
    private int lastHoverCell = -1;
    private int lastPaint = -1;
    private int palettePage;
    private int lastSelected = -1;
    private int GRID_X = 12;
    private int GRID_Y = 26;
    private int CELL = 9;
    private static final String[] PARTS = java.util.Arrays.stream(PcbModules.ALL).map(PcbModules.Module::name).toArray(String[]::new);
    private static final String[] TOOLS = {"Трасса T", "Метка", "Переход", "Вывод G", "Ластик E", "Деталь K", "Контур C", "Выбор V", "Разрыв"};
    private static final String[] TOOL_HINTS = {
            "Провести дорожку на текущем слое", "Метка компонента для пассивного чертежа",
            "Соединить дорожки на разных слоях", "Добавить контакт на краю первого слоя",
            "Очистить ячейку текущего слоя", "Поставить деталь на монтажной стороне (слой 1)",
            "Вырезать ячейку платы или вернуть материал",
            "Выбрать модуль и указать новое место; ПКМ — выбрать и настроить",
            "Нажать на границу ячеек: разорвать соединение или восстановить его"
    };
    private int TOOL_X = 164;
    private static final int TOOL_Y = 27;
    private static final int TOOL_WIDTH = 42;
    private static final int TOOL_HEIGHT = 15;
    private int PALETTE_X = 199;
    private int PALETTE_Y = 153;
    private static final int PALETTE_CELL = 11;
    private static final int PALETTE_COLUMNS = 8;
    private int SETTINGS_Y = 181;
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

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
        super.init();
        TOOL_X = width - 140;
        PALETTE_X = TOOL_X + 35;
        PALETTE_Y = Math.max(143, height - 97);
        SETTINGS_Y = PALETTE_Y + 28;
        sidePalette = width >= 540 && height >= 300;
        canvasLeft = sidePalette ? 144 : 12;
        canvasRight = TOOL_X - 8;
        canvasBottom = height - 84;
        fitCanvas();
        menu.layoutClientSlots(imageWidth, imageHeight);
        String query = catalogSearch == null ? "" : catalogSearch.getValue();
        catalogSearch = new net.minecraft.client.gui.components.EditBox(font, 120, 28, Math.max(100, width - 136), 18, Component.literal("Поиск модулей"));
        catalogSearch.setMaxLength(80);
        catalogSearch.setHint(Component.literal("Имя, раздел, размер или вывод…"));
        catalogSearch.setValue(query);
        catalogSearch.setResponder(value -> catalogScroll = 0);
        placeSearch();
        addRenderableWidget(catalogSearch);
        nominalInput = numberField(SETTINGS_Y + SETTINGS_ROW);
        thresholdInput = numberField(SETTINGS_Y + SETTINGS_ROW * 2);
        numbersAnchor = -2;
        numbersDirty = false;
    }

    private net.minecraft.client.gui.components.EditBox numberField(int y) {
        var field = new net.minecraft.client.gui.components.EditBox(font, PALETTE_X + 2, y, 66, 14, Component.literal("Числовое значение"));
        field.setMaxLength(16);
        field.setFilter(value -> value.matches("[0-9eE+.,\\-]*"));
        field.setResponder(value -> { if (!refreshingNumbers) numbersDirty = true; });
        addRenderableWidget(field);
        return field;
    }

    private static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }

    private void releaseNumbers() {
        nominalInput.setFocused(false); thresholdInput.setFocused(false);
        if (getFocused() == nominalInput || getFocused() == thresholdInput) setFocused(null);
    }

    private void refreshNumbers() {
        boolean changed = numbersAnchor != menu.selected() || numbersDefinition != menu.selectedDefinition() || numbersInstance != menu.instanceId();
        if (changed) { releaseNumbers(); numbersDirty = false; }
        numbersAnchor = menu.selected(); numbersDefinition = menu.selectedDefinition(); numbersInstance = menu.instanceId();
        var module = PcbModules.ALL[menu.part()];
        nominalInput.setVisible(!catalogOpen && !bomOpen && !diagnosticsOpen && module.resistanceSetting());
        thresholdInput.setVisible(!catalogOpen && !bomOpen && !diagnosticsOpen && module.thresholdSetting() && !(menu.getSlot(0).getItem().getItem() instanceof PcbSupplyItem));
        if (!numbersDirty && !nominalInput.isFocused() && !thresholdInput.isFocused()) {
            refreshingNumbers = true;
            nominalInput.setValue(number(menu.nominalValue()));
            thresholdInput.setValue(number(menu.thresholdValue()));
            refreshingNumbers = false;
            nominalInput.setTextColor(0xFFE3E9DE); thresholdInput.setTextColor(0xFFE3E9DE);
        }
    }

    private boolean commitNumbers() {
        if (!numbersDirty) return true;
        double nominal, threshold;
        try {
            nominal = nominalInput.isVisible() ? Double.parseDouble(nominalInput.getValue().replace(',', '.')) : menu.nominalValue();
            threshold = thresholdInput.isVisible() ? Double.parseDouble(thresholdInput.getValue().replace(',', '.')) : menu.thresholdValue();
        } catch (NumberFormatException ignored) {
            nominalInput.setTextColor(0xFFE57C72); thresholdInput.setTextColor(0xFFE57C72); return false;
        }
        var packet = new PcbEditorPackets.Parameters(menu.containerId, numbersAnchor, numbersDefinition, nominal, threshold, numbersInstance);
        if (!menu.applyParameters(packet)) {
            nominalInput.setTextColor(0xFFE57C72); thresholdInput.setTextColor(0xFFE57C72); return false;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        numbersDirty = false;
        diagnostics = java.util.List.of();
        releaseNumbers();
        return true;
    }

    private void openCatalog(boolean open) {
        catalogOpen = open;
        panning = false;
        placeSearch();
        catalogSearch.setFocused(open);
        setFocused(open ? catalogSearch : null);
        lastPaint = -1;
    }

    private void placeSearch() {
        catalogSearch.setX(catalogOpen ? 120 : 12);
        catalogSearch.setY(28);
        catalogSearch.setWidth(catalogOpen ? Math.max(100, width - 136) : 120);
        catalogSearch.setHint(Component.literal(catalogOpen ? "Имя, раздел, размер или вывод…" : "Поиск…"));
        catalogSearch.setVisible(catalogOpen || sidePalette);
    }

    private int sideListTop() { return 54 + (SECTIONS.length + 1) / 2 * 15; }
    private int sideRows() { return Math.max(1, (canvasBottom - sideListTop()) / 30); }
    private int sidePartAt(double x, double y) {
        if (!sidePalette || x < 12 || x >= 132 || y < sideListTop() || y >= sideListTop() + sideRows() * 30) return -1;
        int row = (int) (y - sideListTop()) / 30 + catalogScroll;
        var parts = filteredParts();
        return row < parts.size() ? parts.get(row) : -1;
    }

    private java.util.List<Integer> filteredParts() {
        var result = new java.util.ArrayList<Integer>();
        for (int i = 0; i < PcbModules.ALL.length; i++) {
            var module = PcbModules.ALL[i];
            if ((catalogSection.equals("Все") || catalogSection.equals(module.section())) && module.matches(catalogSearch.getValue())) result.add(i);
        }
        return result;
    }

    private int catalogRows() { return Math.max(1, (height - 78) / 34); }

    private void choosePart(int part) {
        if (!menu.available(part)) return;
        recent.remove(Integer.valueOf(part));
        recent.add(0, part);
        if (recent.size() > 5) recent.remove(5);
        click(PcbEditorMenu.SELECT_PART + part);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nominalInput.isFocused() || thresholdInput.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { commitNumbers(); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { numbersDirty = false; releaseNumbers(); return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                var target = nominalInput.isFocused() && thresholdInput.isVisible() ? thresholdInput : nominalInput;
                nominalInput.setFocused(false); thresholdInput.setFocused(false); target.setFocused(true); setFocused(target); return true;
            }
            (nominalInput.isFocused() ? nominalInput : thresholdInput).keyPressed(key, scan, modifiers);
            return true;
        }
        if (diagnosticsOpen) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) diagnosticsOpen = false;
            return true;
        }
        if (bomOpen) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_M) { bomOpen = false; return true; }
            return true;
        }
        if (catalogOpen) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { openCatalog(false); return true; }
            catalogSearch.keyPressed(key, scan, modifiers);
            return true;
        }
        if (sidePalette && catalogSearch.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { catalogSearch.setFocused(false); setFocused(null); return true; }
            catalogSearch.keyPressed(key, scan, modifiers); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_F7) { click(PcbEditorMenu.CHECK); return true; }
        if (hasControlDown() || hasAltDown()) return super.keyPressed(key, scan, modifiers);
        if (key >= 49 && key <= 53 && key - 49 < recent.size()) { choosePart(recent.get(key - 49)); return true; }
        int tool = switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_V -> PcbEditorMenu.MOVE_TOOL;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_T -> 0;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_G -> 3;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_E -> 4;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_C -> PcbEditorMenu.CUT_TOOL;
            default -> -1;
        };
        if (tool >= 0) { click(PcbEditorMenu.SELECT_TOOL + tool); return true; }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_R) { click(PcbEditorMenu.ROTATE); return true; }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            click(PcbEditorMenu.SELECT_TOOL + 2);
            if (lastPaint >= 0) {
                click(lastPaint);
                click(PcbEditorMenu.LAYER);
                click(PcbEditorMenu.SELECT_TOOL);
            }
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Q && lastHoverCell >= 0) { click(PcbEditorMenu.PICK + lastHoverCell); return true; }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_M) { bomOpen = true; return true; }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) { fitCanvas(); return true; }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_K) { openCatalog(true); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    private void click(int id) {
        if (id == PcbEditorMenu.CHECK && menu.editor() != null) {
            diagnostics = PcbDiagnostics.check(menu.editor().topology());
            diagnosticScroll = 0;
            diagnosticsOpen = true;
        } else if (id >= 0 && id < PcbTopology.CELLS || id >= PcbEditorMenu.EDGE && id < PcbEditorMenu.EDGE + PcbTopology.CELLS * 4
                || id == PcbEditorMenu.CLEAR || id == PcbEditorMenu.LOAD || id == PcbEditorMenu.ROTATE || id == PcbEditorMenu.LEVEL)
            diagnostics = java.util.List.of();
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (diagnosticsOpen) {
            if (mouseY < 22 && mouseX >= width - 52) { diagnosticsOpen = false; return true; }
            int row = (int) (mouseY - 48) / 28 + diagnosticScroll;
            if (mouseY >= 48 && mouseY < 48 + diagnosticRows() * 28 && row < diagnostics.size()) {
                var issue = diagnostics.get(row);
                if (issue.cell() >= 0 && issue.layer() >= 0 && issue.layer() < menu.level()) {
                    int steps = Math.floorMod(issue.layer() - menu.layer(), menu.level());
                    for (int i = 0; i < steps; i++) click(PcbEditorMenu.LAYER);
                    GRID_X = (canvasLeft + canvasRight) / 2 - issue.cell() % 16 * CELL - CELL / 2;
                    GRID_Y = (26 + canvasBottom) / 2 - issue.cell() / 16 * CELL - CELL / 2;
                    diagnosticsOpen = false;
                }
            }
            return true;
        }
        if (!catalogOpen && !bomOpen) {
            boolean nominalHit = nominalInput.isVisible() && nominalInput.isMouseOver(mouseX, mouseY);
            boolean thresholdHit = thresholdInput.isVisible() && thresholdInput.isMouseOver(mouseX, mouseY);
            if (!nominalHit && !thresholdHit && !commitNumbers()) return true;
            if (nominalHit || thresholdHit) {
                var target = nominalHit ? nominalInput : thresholdInput;
                nominalInput.setFocused(false); thresholdInput.setFocused(false);
                target.mouseClicked(mouseX, mouseY, button); setFocused(target); return true;
            }
            releaseNumbers();
        }
        if (bomOpen) { if (mouseY < 22 && mouseX >= width - 52) bomOpen = false; return true; }
        if (catalogOpen) {
            if (mouseY < 22 && mouseX >= width - 52) { openCatalog(false); return true; }
            if (mouseX < 110 && mouseY >= 55) {
                int section = (int) (mouseY - 55) / 24;
                if (section < SECTIONS.length) { catalogSection = SECTIONS[section]; catalogScroll = 0; }
                return true;
            }
            if (mouseX >= 120 && mouseX < width - 16 && mouseY >= 55 && mouseY < 55 + catalogRows() * 34) {
                int row = (int) (mouseY - 55) / 34 + catalogScroll;
                var parts = filteredParts();
                if (row < parts.size() && menu.available(parts.get(row))) { choosePart(parts.get(row)); openCatalog(false); }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (sidePalette) {
            if (catalogSearch.isMouseOver(mouseX, mouseY)) {
                catalogSearch.mouseClicked(mouseX, mouseY, button); setFocused(catalogSearch); return true;
            }
            catalogSearch.setFocused(false);
            if (getFocused() == catalogSearch) setFocused(null);
            if (localX >= 12 && localX < 132 && localY >= 50 && localY < sideListTop() - 4) {
                int section = (int) (localY - 50) / 15 * 2 + (int) (localX - 12) / 60;
                if (section < SECTIONS.length) { catalogSection = SECTIONS[section]; catalogScroll = 0; }
                return true;
            }
            int candidate = sidePartAt(localX, localY);
            if (candidate >= 0) { if (button == 0) choosePart(candidate); return true; }
        }
        if (localX >= 184 && localX < TOOL_X - 8 && localY >= height - 74 && localY < height - 48) { bomOpen = true; return true; }
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
            openCatalog(true);
            return true;
        }
        int part = partAt(localX, localY);
        if (part >= 0) {
            if (button == 0) choosePart(part);
            return true;
        }
        if (localX >= PALETTE_X && localX < TOOL_X + 126 && localY >= SETTINGS_Y
                && localY < SETTINGS_Y + SETTINGS_ROW * PART_BUTTONS.length) {
            int action = PART_BUTTONS[(int) (localY - SETTINGS_Y) / SETTINGS_ROW];
            if (action == PcbEditorMenu.ROTATE && button == 1
                    && PcbWiring.KINDS[menu.part()] == PcbCircuitDevice.Kind.RELAY && PcbModules.ALL[menu.part()].pins() == 4) action = PcbEditorMenu.CONTACT;
            if (action == PcbEditorMenu.ROTATE || action == PcbEditorMenu.CONTACT || menu.getSlot(0).getItem().getItem() instanceof PcbSupplyItem) click(action);
            return true;
        }
        if (!insideCanvas(localX, localY)) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 2) { panning = true; return true; }
        int x = (int) Math.floor((localX - GRID_X) / CELL);
        int y = (int) Math.floor((localY - GRID_Y) / CELL);
        if (x >= 0 && y >= 0 && x < PcbTopology.SIZE && y < PcbTopology.SIZE) {
            if (button == 0 && menu.tool() == PcbEditorMenu.EDGE_TOOL) {
                double sx = localX - GRID_X - x * CELL, sy = localY - GRID_Y - y * CELL;
                int direction = Math.min(sx, CELL - sx) <= Math.min(sy, CELL - sy) ? (sx < CELL / 2.0 ? 0 : 1) : (sy < CELL / 2.0 ? 2 : 3);
                click(PcbEditorMenu.EDGE + (y * PcbTopology.SIZE + x) * 4 + direction);
            } else if (button == 0) {
                click(y * PcbTopology.SIZE + x);
                if (menu.tool() == 0 || menu.tool() == 4) lastPaint = y * PcbTopology.SIZE + x;
            }
            else if (button == 1) click(PcbEditorMenu.PICK + y * PcbTopology.SIZE + x);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (bomOpen || diagnosticsOpen) return true;
        if (catalogOpen) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        if (button == 2 && panning) { GRID_X += (int) dx; GRID_Y += (int) dy; return true; }
        if (button != 0 || lastPaint < 0 || menu.tool() != 0 && menu.tool() != 4)
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        int x = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int y = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        if (!insideCanvas(mouseX - leftPos, mouseY - topPos) || x < 0 || y < 0 || x >= PcbTopology.SIZE || y >= PcbTopology.SIZE) { lastPaint = -1; return true; }
        int oldX = lastPaint % PcbTopology.SIZE, oldY = lastPaint / PcbTopology.SIZE;
        while (oldX != x) { oldX += Integer.signum(x - oldX); click(oldY * PcbTopology.SIZE + oldX); }
        while (oldY != y) { oldY += Integer.signum(y - oldY); click(oldY * PcbTopology.SIZE + oldX); }
        lastPaint = y * PcbTopology.SIZE + x;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        lastPaint = -1;
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
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
        if (diagnosticsOpen) { diagnosticScroll = Math.clamp(diagnosticScroll + (vertical < 0 ? 1 : -1), 0, Math.max(0, diagnostics.size() - diagnosticRows())); return true; }
        if (bomOpen) { bomScroll = Math.clamp(bomScroll + (vertical < 0 ? 1 : -1), 0, Math.max(0, bill().size() - Math.max(1, (height - 64) / 24))); return true; }
        if (catalogOpen) {
            catalogScroll = Math.clamp(catalogScroll + (vertical < 0 ? 1 : -1), 0, Math.max(0, filteredParts().size() - catalogRows()));
            return true;
        }
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (sidePalette && x >= 12 && x < 132 && y >= sideListTop() && y < canvasBottom) {
            catalogScroll = Math.clamp(catalogScroll + (vertical < 0 ? 1 : -1), 0, Math.max(0, filteredParts().size() - sideRows())); return true;
        }
        if (insideCanvas(x, y)) {
            int next = Math.clamp(CELL + (vertical > 0 ? 2 : -2), 5, 25);
            double worldX = (x - GRID_X) / CELL, worldY = (y - GRID_Y) / CELL;
            GRID_X = (int) Math.round(x - worldX * next);
            GRID_Y = (int) Math.round(y - worldY * next);
            CELL = next;
            return true;
        }
        if (x >= TOOL_X && x < TOOL_X + 126 && y >= PALETTE_Y && y < PALETTE_Y + 22) {
            palettePage = Math.floorMod(palettePage + (vertical < 0 ? 1 : -1), pageCount());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private int toolAt(double x, double y) {
        if (x < TOOL_X || x >= TOOL_X + 3 * TOOL_WIDTH || y < TOOL_Y || y >= TOOL_Y + 3 * TOOL_HEIGHT) return -1;
        int tool = (int) ((y - TOOL_Y) / TOOL_HEIGHT) * 3 + (int) ((x - TOOL_X) / TOOL_WIDTH);
        return tool < TOOLS.length ? tool : -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshNumbers();
        int hoverX = mouseX - leftPos - GRID_X, hoverY = mouseY - topPos - GRID_Y;
        lastHoverCell = insideCanvas(mouseX - leftPos, mouseY - topPos) && hoverX >= 0 && hoverY >= 0 && hoverX < CELL * 16 && hoverY < CELL * 16
                ? hoverY / CELL * 16 + hoverX / CELL : -1;
        if (lastSelected != menu.part()) {
            lastSelected = menu.part();
            palettePage = menu.part() / 16;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (diagnosticsOpen) { renderDiagnostics(graphics, mouseX, mouseY); return; }
        if (bomOpen) { renderBill(graphics); return; }
        if (catalogOpen) { renderCatalog(graphics, mouseX, mouseY, partialTick); return; }
        renderTooltip(graphics, mouseX, mouseY);
        int sidePart = sidePartAt(mouseX - leftPos, mouseY - topPos);
        if (sidePart >= 0) {
            var entry = PcbModules.ALL[sidePart];
            var tips = new java.util.ArrayList<Component>();
            tips.add(Component.literal(entry.name() + " · " + entry.width() + "×" + entry.height() + " · " + (menu.available(sidePart) ? "L" : "с L") + entry.level()));
            for (int pin = 0; pin < entry.pins(); pin++) tips.add(Component.literal(entry.pinLabel(pin)));
            graphics.renderTooltip(font, tips, Optional.empty(), mouseX, mouseY); return;
        }
        var inputModule = PcbModules.ALL[menu.part()];
        if (nominalInput.isVisible() && nominalInput.isMouseOver(mouseX, mouseY)) {
            String hint = "От " + number(PcbParameters.minimum(inputModule)) + " до " + number(PcbParameters.maximum(inputModule)) + " " + PcbParameters.unit(inputModule)
                    + (inputModule.timed() ? "; шаг 0,05 с" : "; ряд: 10, 47, 100, 220, 470, 1000") + ". Enter — применить";
            graphics.renderTooltip(font, Component.literal(hint), mouseX, mouseY); return;
        }
        if (thresholdInput.isVisible() && thresholdInput.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal("Больше 0, до " + number(PcbParameters.maxThreshold(inputModule)) + (PcbParameters.amps(inputModule) ? " А" : " В") + ". Enter — применить"), mouseX, mouseY); return;
        }
        int tool = toolAt(mouseX - leftPos, mouseY - topPos);
        if (tool >= 0) {
            graphics.renderTooltip(font, Component.literal(TOOL_HINTS[tool]), mouseX, mouseY);
            return;
        }
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        if (localX >= TOOL_X && localX < PALETTE_X - 3 && localY >= PALETTE_Y && localY < PALETTE_Y + 22) {
            graphics.renderTooltip(font, Component.literal("Каталог K: поиск и разделы; колесо — быстрые страницы"), mouseX, mouseY);
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
        if (localX >= TOOL_X && localX < TOOL_X + 126 && localY >= height - 20 && localY < height - 7) {
            graphics.renderTooltip(font, Component.literal(menu.statusText()), mouseX, mouseY);
            return;
        }
        if (localX >= width - 36 && localX < width - 20 && localY >= 8 && localY < 24 && !menu.getSlot(0).hasItem()) {
            graphics.renderTooltip(font, Component.literal("Бумага или чертёж. Чистую плату можно оставить в инвентаре."),
                    mouseX, mouseY);
            return;
        }
        if (!insideCanvas(localX, localY)) return;
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
                    graphics.renderTooltip(font, Component.literal(module.name() + " · " + module.pinLabel(pin)), mouseX, mouseY);
                    return;
                }
            }
            if (highlighted[menu.layer() * PcbTopology.CELLS + cell]) {
                int contacts = 0;
                for (int anchor = 0; anchor < definitions.length; anchor++) {
                    if (!PcbFootprint.valid(definitions[anchor])) continue;
                    for (int pin = 0; pin < PcbModules.get(definitions[anchor]).pins(); pin++) {
                        int pad = PcbWiring.pinCell(anchor, definitions[anchor], pin);
                        if (pad >= 0 && highlighted[pad]) contacts++;
                    }
                }
                graphics.renderTooltip(font, Component.literal("Сеть · контактов модулей: " + contacts), mouseX, mouseY);
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
        graphics.fill(leftPos + canvasLeft - 2, topPos + 24, leftPos + canvasRight + 2, topPos + canvasBottom + 2, 0xFF0B1114);
        graphics.enableScissor(leftPos + canvasLeft, topPos + 26, leftPos + canvasRight, topPos + canvasBottom);
        PcbEditorBlockEntity editor = menu.editor();
        CompoundTag topology = editor == null ? PcbTopology.create(menu.level()) : editor.topology();
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        highlightNetwork(topology);
        for (int y = 0; y < PcbTopology.SIZE; y++) {
            for (int x = 0; x < PcbTopology.SIZE; x++) {
                int cell = y * PcbTopology.SIZE + x;
                int value = PcbTopology.get(topology, menu.layer(), cell);
                int color = PcbTopology.cut(topology, cell) ? 0xFF0B1114 : switch (value) {
                    case PcbTopology.TRACE -> (x + y) % 2 == 0 ? 0xFF153C32 : 0xFF12362D;
                    case PcbTopology.COMPONENT -> 0xFFE3D36A;
                    case PcbTopology.VIA -> 0xFF69D4DB;
                    case PcbTopology.TERMINAL -> 0xFF79D878;
                    default -> (x + y) % 2 == 0 ? 0xFF153C32 : 0xFF12362D;
                };
                int sx = leftPos + GRID_X + x * CELL;
                int sy = topPos + GRID_Y + y * CELL;
                graphics.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, color);
                if (PcbWiring.conductor(value)) {
                    int ink = value == PcbTopology.VIA ? 0xFF69D4DB : value == PcbTopology.TERMINAL ? 0xFF79D878 : 0xFFD17B45;
                    if (highlighted[menu.layer() * PcbTopology.CELLS + cell]) ink = 0xFFFFE59A;
                    int cx = sx + CELL / 2, cy = sy + CELL / 2;
                    int radius = value == PcbTopology.TRACE ? 1 : 2;
                    graphics.fill(cx - radius, cy - radius, cx + radius + 1, cy + radius + 1, ink);
                    if (x > 0 && PcbBoardRenderer.joins(topology, definitions, menu.layer(), cell, cell - 1)) graphics.fill(sx, cy, cx, cy + 1, ink);
                    if (x < 15 && PcbBoardRenderer.joins(topology, definitions, menu.layer(), cell, cell + 1)) graphics.fill(cx, cy, sx + CELL, cy + 1, ink);
                    if (y > 0 && PcbBoardRenderer.joins(topology, definitions, menu.layer(), cell, cell - 16)) graphics.fill(cx, sy, cx + 1, cy, ink);
                    if (y < 15 && PcbBoardRenderer.joins(topology, definitions, menu.layer(), cell, cell + 16)) graphics.fill(cx, cy, cx + 1, sy + CELL, ink);
                }
                if (PcbTopology.edgeCut(topology, menu.layer(), cell, 1)) graphics.fill(sx + CELL - 1, sy + CELL / 2 - 2, sx + CELL + 1, sy + CELL / 2 + 2, 0xFFE57C72);
                if (PcbTopology.edgeCut(topology, menu.layer(), cell, 3)) graphics.fill(sx + CELL / 2 - 2, sy + CELL - 1, sx + CELL / 2 + 2, sy + CELL + 1, 0xFFE57C72);

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
        if (insideCanvas(mouseX - leftPos, mouseY - topPos) && hoverX >= 0 && hoverY >= 0 && hoverX < CELL * PcbTopology.SIZE && hoverY < CELL * PcbTopology.SIZE) {
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
                    if (CELL >= 12) graphics.drawString(font, PcbModules.get(definition).pinType(pin).symbol, sx + 2, sy + 2, 0xFFEDEDDD, false);
                }
            }
        }
        for (var issue : diagnostics) {
            if (issue.layer() != menu.layer() || issue.cell() < 0) continue;
            int sx = leftPos + GRID_X + issue.cell() % 16 * CELL, sy = topPos + GRID_Y + issue.cell() / 16 * CELL;
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 200);
            graphics.renderOutline(sx, sy, CELL, CELL, 0xFFFF7566);
            graphics.drawString(font, "!", sx + 1, sy + 1, 0xFFFFB294, false);
            graphics.pose().popPose();
        }
        graphics.disableScissor();
        if (sidePalette) renderSidePalette(graphics);
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

    private void renderSidePalette(GuiGraphics graphics) {
        graphics.fill(8, 24, 136, canvasBottom + 2, 0xFF1A292F);
        for (int section = 0; section < SECTIONS.length; section++) {
            String name = SECTIONS[section];
            long count = java.util.Arrays.stream(PcbModules.ALL).filter(module -> name.equals("Все") || name.equals(module.section())).count();
            int x = 12 + section % 2 * 60, y = 50 + section / 2 * 15;
            graphics.fill(x, y, x + 58, y + 13, catalogSection.equals(name) ? 0xFF3C6658 : 0xFF2A3E43);
            String shortName = name.equals("Все") ? name : font.plainSubstrByWidth(name, 35);
            graphics.drawString(font, shortName + " " + count, x + 2, y + 3, 0xFFDCE7DF, false);
        }
        var parts = filteredParts();
        catalogScroll = Math.min(catalogScroll, Math.max(0, parts.size() - sideRows()));
        for (int row = 0; row < sideRows() && row + catalogScroll < parts.size(); row++) {
            int part = parts.get(row + catalogScroll), y = sideListTop() + row * 30;
            var module = PcbModules.ALL[part]; boolean available = menu.available(part);
            graphics.fill(12, y, 132, y + 28, part == menu.part() ? 0xFF36584F : 0xFF263A40);
            drawPart(graphics, part, 15, y + 5, 1f);
            if (!available) {
                graphics.pose().pushPose(); graphics.pose().translate(0, 0, 200);
                graphics.fill(15, y + 5, 31, y + 21, 0x99141E23); graphics.pose().popPose();
            }
            graphics.drawString(font, font.plainSubstrByWidth(module.name(), 94), 35, y + 4, available ? 0xFFE2E9DF : 0xFF7F9189, false);
            graphics.drawString(font, module.width() + "×" + module.height() + " · " + (available ? "L" : "с L") + module.level(), 35, y + 17, available ? 0xFFA4BFAE : 0xFF7F9189, false);
        }
        if (parts.isEmpty()) graphics.drawString(font, "Ничего не найдено", 14, sideListTop() + 6, 0xFFAFBFB4, false);
    }

    private int diagnosticRows() { return Math.max(1, (height - 68) / 28); }

    private void renderDiagnostics(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 500);
        graphics.fill(0, 0, width, height, 0xFF141E23);
        graphics.drawString(font, "Проверка платы · F7", 12, 9, 0xFFE7ECE8, false);
        graphics.drawString(font, "Закрыть", width - 48, 9, 0xFFD3DDD7, false);
        graphics.drawString(font, diagnostics.isEmpty() ? "Ошибок не найдено" : "Ошибок: " + diagnostics.size() + ". Нажмите строку, чтобы перейти к месту", 12, 31, diagnostics.isEmpty() ? 0xFF8DD5A4 : 0xFFE7B39A, false);
        for (int row = 0; row < diagnosticRows() && row + diagnosticScroll < diagnostics.size(); row++) {
            var issue = diagnostics.get(row + diagnosticScroll);
            int y = 48 + row * 28;
            graphics.fill(8, y, width - 8, y + 26, 0xFF293B40);
            String location = issue.cell() < 0 ? "Общая" : "L" + (issue.layer() + 1) + " · " + (issue.cell() % 16 + 1) + ":" + (issue.cell() / 16 + 1);
            graphics.drawString(font, location, 12, y + 3, 0xFFFF9A81, false);
            graphics.drawString(font, font.plainSubstrByWidth(issue.message(), width - 30), 12, y + 15, 0xFFE0E8E1, false);
            if (mouseX >= 8 && mouseX < width - 8 && mouseY >= y && mouseY < y + 26)
                graphics.renderTooltip(font, Component.literal(issue.message()), mouseX, mouseY);
        }
        graphics.drawString(font, "Esc: вернуться · колесо: список", 12, height - 12, 0xFF9FB5AD, false);
        graphics.pose().popPose();
    }

    private java.util.Map<net.minecraft.world.item.Item, Integer> bill() {
        var needed = new java.util.LinkedHashMap<net.minecraft.world.item.Item, Integer>();
        if (menu.editor() == null) return needed;
        for (int definition : menu.editor().topology().getIntArray(PcbWiring.KEY)) {
            if (!PcbFootprint.valid(definition)) continue;
            var item = PcbAssembly.item(PcbModules.get(definition).kind());
            if (item != net.minecraft.world.item.Items.AIR) needed.merge(item, 1, Integer::sum);
        }
        return needed;
    }

    private void renderBill(GuiGraphics graphics) {
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 500);
        graphics.fill(0, 0, width, height, 0xFF141E23);
        graphics.drawString(font, "Ведомость деталей", 12, 9, 0xFFE7ECE8, false);
        graphics.drawString(font, "Закрыть", width - 48, 9, 0xFFD3DDD7, false);
        graphics.drawString(font, "Предмет", 38, 32, 0xFF9FB5AD, false);
        graphics.drawString(font, "Нужно / есть / не хватает", width - 170, 32, 0xFF9FB5AD, false);
        var entries = new java.util.ArrayList<>(bill().entrySet());
        int rows = Math.max(1, (height - 64) / 24);
        bomScroll = Math.min(bomScroll, Math.max(0, entries.size() - rows));
        for (int row = 0; row < rows && row + bomScroll < entries.size(); row++) {
            var entry = entries.get(row + bomScroll);
            int y = 49 + row * 24;
            int have = minecraft.player.getInventory().items.stream().filter(stack -> stack.is(entry.getKey())).mapToInt(ItemStack::getCount).sum();
            int missing = Math.max(0, entry.getValue() - have);
            graphics.fill(8, y, width - 8, y + 22, 0xFF26383D);
            graphics.renderItem(new ItemStack(entry.getKey()), 12, y + 3);
            graphics.drawString(font, font.plainSubstrByWidth(new ItemStack(entry.getKey()).getHoverName().getString(), Math.max(50, width - 222)), 36, y + 7, 0xFFE0E8E1, false);
            graphics.drawString(font, entry.getValue() + " / " + have + " / " + missing, width - 150, y + 7, missing == 0 ? 0xFF8DD5A4 : 0xFFE59B82, false);
        }
        graphics.drawString(font, "M / Esc: вернуться · колесо: список", 12, height - 12, 0xFF9FB5AD, false);
        graphics.pose().popPose();
    }

    private void highlightNetwork(CompoundTag topology) {
        java.util.Arrays.fill(highlighted, false);
        if (lastHoverCell < 0 || !PcbWiring.conductor(PcbTopology.get(topology, menu.layer(), lastHoverCell))) return;
        var queue = new java.util.ArrayDeque<Integer>();
        int first = menu.layer() * PcbTopology.CELLS + lastHoverCell;
        queue.add(first); highlighted[first] = true;
        while (!queue.isEmpty()) {
            int index = queue.removeFirst(), layer = index / PcbTopology.CELLS, cell = index % PcbTopology.CELLS;
            for (int direction = 0; direction < 4; direction++) {
                int next = PcbTopology.neighbor(cell, direction);
                if (next < 0 || !PcbTopology.connected(topology, layer, cell, next)
                        || !PcbWiring.conductor(PcbTopology.get(topology, layer, next))) continue;
                int target = layer * PcbTopology.CELLS + next;
                if (!highlighted[target]) { highlighted[target] = true; queue.add(target); }
            }
            for (int other = 0; other < menu.level(); other++) {
                int target = other * PcbTopology.CELLS + cell;
                if (!highlighted[target] && PcbWiring.conductor(PcbTopology.get(topology, other, cell))
                        && (PcbTopology.get(topology, layer, cell) == PcbTopology.VIA || PcbTopology.get(topology, other, cell) == PcbTopology.VIA)) {
                    highlighted[target] = true; queue.add(target);
                }
            }
        }
    }

    private void renderCatalog(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.fill(0, 0, width, height, 0xFF141E23);
        graphics.drawString(font, "Каталог модулей · L" + menu.level(), 12, 9, 0xFFE7ECE8, false);
        graphics.drawString(font, "Закрыть", width - 48, 9, 0xFFD3DDD7, false);
        graphics.drawString(font, "Разделы", 12, 33, 0xFF9FB5AD, false);
        for (int section = 0; section < SECTIONS.length; section++) {
            String name = SECTIONS[section];
            long count = java.util.Arrays.stream(PcbModules.ALL).filter(module -> name.equals("Все") || name.equals(module.section())).count();
            int y = 55 + section * 24;
            graphics.fill(8, y, 110, y + 22, catalogSection.equals(name) ? 0xFF365E51 : 0xFF22343A);
            graphics.drawString(font, name + " " + count, 12, y + 7, 0xFFE0E8E1, false);
        }
        catalogSearch.render(graphics, mouseX, mouseY, partialTick);
        var parts = filteredParts();
        catalogScroll = Math.min(catalogScroll, Math.max(0, parts.size() - catalogRows()));
        for (int row = 0; row < catalogRows() && row + catalogScroll < parts.size(); row++) {
            int part = parts.get(row + catalogScroll);
            var module = PcbModules.ALL[part];
            int y = 55 + row * 34;
            boolean available = menu.available(part);
            graphics.fill(120, y, width - 16, y + 32, available ? 0xFF293E43 : 0xFF202B30);
            drawPart(graphics, part, 125, y + 3, 1.4f);
            if (!available) {
                graphics.pose().pushPose(); graphics.pose().translate(0, 0, 200);
                graphics.fill(125, y + 3, 148, y + 26, 0x99141E23); graphics.pose().popPose();
            }
            graphics.drawString(font, font.plainSubstrByWidth(module.name(), Math.max(40, width - 178)), 154, y + 4, available ? 0xFFE4EAE2 : 0xFF88918D, false);
            String detail = module.width() + "×" + module.height() + " · " + module.section() + " · " + (available ? "L" : "с L") + module.level();
            graphics.drawString(font, detail, 154, y + 19, available ? 0xFFAFC4B9 : 0xFF737F79, false);
            if (mouseX >= 120 && mouseX < width - 16 && mouseY >= y && mouseY < y + 32) {
                graphics.renderOutline(120, y, width - 136, 32, 0xFF81B8A1);
            }
        }
        if (parts.isEmpty()) graphics.drawString(font, "Нет подходящих модулей", 124, 63, 0xFFB2C0B8, false);
        graphics.drawString(font, "Найдено: " + parts.size() + " · колесо: список · Esc: вернуться", 120, height - 14, 0xFF9CB4A6, false);
        graphics.pose().popPose();
    }

    private void drawModule(GuiGraphics graphics, int definition, int cell, int color, boolean ghost) {
        int x = leftPos + GRID_X + cell % PcbTopology.SIZE * CELL;
        int y = topPos + GRID_Y + cell / PcbTopology.SIZE * CELL;
        int w = PcbFootprint.width(definition) * CELL;
        int h = PcbFootprint.height(definition) * CELL;
        // Предпросмотр обрезается по рабочему полю, даже если корпус выходит за край.

        graphics.fill(x, y, x + w - 1, y + h - 1, color);
        graphics.renderOutline(x, y, w - 1, h - 1, ghost ? 0xFFE8ECCC : 0xFF9CBFB0);
        drawModel(graphics, definition, x, y, CELL);
    }

    private static void drawModel(GuiGraphics graphics, int definition, float x, float y, float cellSize) {
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 150);
        graphics.pose().scale(16 * cellSize, -16 * cellSize, 16 * cellSize);
        graphics.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        var vertices = graphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        PcbPartModels.render(definition, 0, 0, false, 0L, false, false, 50,
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
        var selectedModule = PcbModules.ALL[menu.part()];
        if (PALETTE_Y >= 205) {
            graphics.drawString(font, font.plainSubstrByWidth(selectedModule.name(), 124), TOOL_X, 154, 0xFFE3E9DE, false);
            graphics.drawString(font, selectedModule.width() + "×" + selectedModule.height() + " · L" + selectedModule.level(), TOOL_X, 168, 0xFFA4C1B2, false);
            for (int pin = 0; pin < selectedModule.pins() && 182 + pin * 11 < PALETTE_Y - 12; pin++)
                graphics.drawString(font, font.plainSubstrByWidth(selectedModule.pinLabel(pin), 124), TOOL_X, 182 + pin * 11, selectedModule.pinType(pin).color, false);
        }
        graphics.drawCenteredString(font, (palettePage + 1) + "/" + pageCount(), TOOL_X + 16, PALETTE_Y + 6, 0xFFFFFFFF);
        PcbCircuitDevice.Kind kind = PcbWiring.KINDS[menu.part()];
        double value = PcbWiring.OHMS[menu.resistance()];
        if (kind == PcbCircuitDevice.Kind.LAMP || kind.led()) value = PcbCircuitDevice.indicatorResistance(value);
        ItemStack input = menu.getSlot(0).getItem();
        boolean supply = input.getItem() instanceof PcbSupplyItem;
        double limit = supply ? PcbSupplyBlock.VOLTS[PcbSupplyItem.setting(input)] : PcbWiring.VOLTS[menu.threshold()];
        String[] arrows = {"→", "↓", "←", "↑"};
        String orientation = arrows[menu.rotation()];
        if (kind == PcbCircuitDevice.Kind.RELAY) orientation += " · НО/НЗ";
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
        partLabels[1] = nominalInput.isVisible() ? PcbParameters.unit(PcbModules.ALL[menu.part()]) : partLabels[1];
        partLabels[2] = thresholdInput.isVisible() ? (PcbParameters.amps(PcbModules.ALL[menu.part()]) ? "А" : "В") : partLabels[2];
        for (int row = 0; row < partLabels.length; row++) {
            if (row == 1 && nominalInput.isVisible() || row == 2 && thresholdInput.isVisible()) {
                graphics.drawString(font, partLabels[row], PALETTE_X + 72, SETTINGS_Y + 3 + row * SETTINGS_ROW, 0xFFE3E9DE, false);
                continue;
            }
            graphics.drawCenteredString(font, partLabels[row], PALETTE_X + 45, SETTINGS_Y + 3 + row * SETTINGS_ROW, 0xFFFFFFFF);
        }
        if (TOOL_X > 260) {
            graphics.drawString(font, "Ведомость M", 184, height - 68, 0xFFE0E8E1, false);
            graphics.drawString(font, "Деталей: " + bill().values().stream().mapToInt(Integer::intValue).sum() + "/64", 184, height - 53, 0xFF9FB5AD, false);
            graphics.drawString(font, "Масштаб " + Math.round(CELL / 9.0 * 100) + "% · Home", 184, height - 38, 0xFF9FB5AD, false);
        }
        int color = menu.status() == 1 ? 0xFF79D878 : menu.status() >= 4 ? 0xFFE57C72 : 0xFFB6C0BA;
        String status = menu.status() == 0 ? "Проект не проверен" : menu.statusText();
        if (font.width(status) > 124) status = font.plainSubstrByWidth(status, 116) + "…";
        graphics.drawString(font, status, TOOL_X + 2, height - 20, color, false);
    }

}
