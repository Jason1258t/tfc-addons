package ee.extras.pcb;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Русский экран редактора: клик по ячейке применяет выбранный инструмент на текущем слое. */
public class PcbEditorScreen extends AbstractContainerScreen<PcbEditorMenu> {
    private static final int GRID_X = 12;
    private static final int GRID_Y = 26;
    private static final int CELL = 7;
    private static final String[] PARTS = {"Резистор", "Реле", "Кнопка", "Переключатель",
            "Индикатор", "Редстоун-вход", "Редстоун-выход", "Конденсатор", "Диод", "Предохранитель",
            "Вольтметр", "Амперметр", "Транзистор"};
    private static final int[] PART_BUTTONS = {PcbEditorMenu.PART, PcbEditorMenu.ROTATE, PcbEditorMenu.OHMS, PcbEditorMenu.VOLTS};
    private static final int[] BUTTON_Y = {28, 46, 64, 82, 100, 118, 136};
    private static final int[] BUTTON_ID = {PcbEditorMenu.TOOL, PcbEditorMenu.LAYER, PcbEditorMenu.LEVEL,
            PcbEditorMenu.CHECK, PcbEditorMenu.SAVE, PcbEditorMenu.LOAD, PcbEditorMenu.CLEAR};

    public PcbEditorScreen(PcbEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 280;
        imageHeight = 234;
        inventoryLabelY = 10000;
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localX >= 140 && localX < 266) {
            for (int i = 0; i < BUTTON_Y.length; i++) {
                if (localY >= BUTTON_Y[i] && localY < BUTTON_Y[i] + 18) {
                    click(BUTTON_ID[i]);
                    return true;
                }
            }
        }
        if (localX >= 175 && localX < 266 && localY >= 155 && localY < 227) {
            int action = PART_BUTTONS[(int) (localY - 155) / 18];
            if (action == PcbEditorMenu.ROTATE && button == 1
                    && PcbWiring.KINDS[menu.part()] == PcbCircuitDevice.Kind.RELAY) action = PcbEditorMenu.CONTACT;
            click(action);
            return true;
        }
        int x = (int) Math.floor((localX - GRID_X) / CELL);
        int y = (int) Math.floor((localY - GRID_Y) / CELL);
        if (x >= 0 && y >= 0 && x < PcbTopology.SIZE && y < PcbTopology.SIZE) {
            click(y * PcbTopology.SIZE + x);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int x = mouseX - leftPos - GRID_X;
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
        int index = menu.layer() * PcbTopology.CELLS + cell;
        if (index >= definitions.length || definitions[index] == 0) return;
        int type = PcbWiring.type(definitions[index]);
        if (type >= PARTS.length) return;
        var item = PcbAssembly.item(PcbWiring.KINDS[type]);
        var component = new net.minecraft.world.item.ItemStack(item);
        graphics.renderTooltip(font, List.of(Component.literal(PARTS[type]), component.getHoverName()), Optional.empty(),
                mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20252A);
        graphics.fill(leftPos + GRID_X - 2, topPos + GRID_Y - 2, leftPos + GRID_X + 112 + 2,
                topPos + GRID_Y + 112 + 2, 0xFF0B1114);
        PcbEditorBlockEntity editor = menu.editor();
        CompoundTag topology = editor == null ? PcbTopology.create(menu.level()) : editor.topology();
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        for (int y = 0; y < PcbTopology.SIZE; y++) {
            for (int x = 0; x < PcbTopology.SIZE; x++) {
                int cell = y * PcbTopology.SIZE + x;
                int value = PcbTopology.get(topology, menu.layer(), cell);
                int color = switch (value) {
                    case PcbTopology.TRACE -> 0xFFD17B45;
                    case PcbTopology.COMPONENT -> 0xFFE3D36A;
                    case PcbTopology.VIA -> 0xFF69D4DB;
                    case PcbTopology.TERMINAL -> 0xFF79D878;
                    default -> (x + y) % 2 == 0 ? 0xFF153C32 : 0xFF12362D;
                };
                int sx = leftPos + GRID_X + x * CELL;
                int sy = topPos + GRID_Y + y * CELL;
                graphics.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, color);
                int index = menu.layer() * PcbTopology.CELLS + cell;
                if (value == PcbTopology.COMPONENT && index < definitions.length && definitions[index] != 0) {
                    if (PcbWiring.vertical(definitions[index])) graphics.fill(sx + 2, sy, sx + 4, sy + 6, 0xFF553F0D);
                    else graphics.fill(sx, sy + 2, sx + 6, sy + 4, 0xFF553F0D);
                }
            }
        }
        for (int row = 0; row < PART_BUTTONS.length; row++) {
            int y = topPos + 155 + row * 18;
            graphics.fill(leftPos + 175, y, leftPos + 266, y + 17, 0xFF66706C);
        }
        for (int y : BUTTON_Y) {
            graphics.fill(leftPos + 140, topPos + y, leftPos + 266, topPos + y + 18, 0xFF101416);
            graphics.fill(leftPos + 141, topPos + y + 1, leftPos + 265, topPos + y + 17, 0xFF66706C);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 8, 0xFFE7ECE8, false);
        graphics.drawString(font, Component.translatable("ee_extras.pcb.tool_value", toolName()), 140, 18, 0xFFD5DDD8, false);
        for (int i = 0; i < BUTTON_Y.length; i++) {
            Component label = switch (i) {
                case 1 -> Component.translatable("ee_extras.pcb.layer_value", menu.layer() + 1);
                case 2 -> Component.literal("Уровень: L" + menu.level());
                case 4 -> menu.getSlot(0).getItem().is(PcbItems.BURNT_BOARD.get())
                        ? Component.translatable("ee_extras.pcb.salvage")
                        : Component.translatable(!menu.getSlot(0).getItem().isEmpty() && PcbBoardItem.level(menu.getSlot(0).getItem()) == 0
                                ? "ee_extras.pcb.save" : "ee_extras.pcb.assemble");
                default -> Component.translatable("ee_extras.pcb." + new String[] {"tool", "layer", "level", "check", "save", "load", "clear"}[i]);
            };
            graphics.drawCenteredString(font, label, 203, BUTTON_Y[i] + 5, 0xFFFFFFFF);
        }
        PcbCircuitDevice.Kind kind = PcbWiring.KINDS[menu.part()];
        double value = PcbWiring.OHMS[menu.resistance()];
        double limit = PcbWiring.VOLTS[menu.threshold()];
        String[] arrows = {"→", "↓", "←", "↑"};
        String orientation = arrows[menu.rotation()];
        if (kind == PcbCircuitDevice.Kind.RELAY) orientation += menu.normallyClosed() ? " · НЗ" : " · НО";
        String[] partLabels = {PARTS[menu.part()], "Контакт " + orientation,
                kind == PcbCircuitDevice.Kind.CAPACITOR ? (int) value + " мФ" : (int) value + " Ом",
                kind == PcbCircuitDevice.Kind.FUSE || kind == PcbCircuitDevice.Kind.AMMETER
                        ? limit / 100 + " А" : (int) limit + " В"};
        for (int row = 0; row < partLabels.length; row++) {
            graphics.drawCenteredString(font, partLabels[row], 220, 160 + row * 18, 0xFFFFFFFF);
        }
        int color = menu.status() == 1 ? 0xFF79D878 : menu.status() >= 4 ? 0xFFE57C72 : 0xFFB6C0BA;
        graphics.drawString(font, menu.statusText(), 12, 144, color, false);
    }

    private Component toolName() {
        if (menu.tool() == 5) return Component.literal(PARTS[menu.part()]);
        return Component.translatable("ee_extras.pcb.tool." + menu.tool());
    }
}
