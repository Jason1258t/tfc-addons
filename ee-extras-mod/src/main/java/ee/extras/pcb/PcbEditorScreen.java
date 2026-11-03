package ee.extras.pcb;

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
        int x = (int) Math.floor((localX - GRID_X) / CELL);
        int y = (int) Math.floor((localY - GRID_Y) / CELL);
        if (x >= 0 && y >= 0 && x < PcbTopology.SIZE && y < PcbTopology.SIZE) {
            click(y * PcbTopology.SIZE + x);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20252A);
        graphics.fill(leftPos + GRID_X - 2, topPos + GRID_Y - 2, leftPos + GRID_X + 112 + 2,
                topPos + GRID_Y + 112 + 2, 0xFF0B1114);
        PcbEditorBlockEntity editor = menu.editor();
        CompoundTag topology = editor == null ? PcbTopology.create(menu.level()) : editor.topology();
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
            }
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
                default -> Component.translatable("ee_extras.pcb." + new String[] {"tool", "layer", "level", "check", "save", "load", "clear"}[i]);
            };
            graphics.drawCenteredString(font, label, 203, BUTTON_Y[i] + 5, 0xFFFFFFFF);
        }
        int color = menu.status() == 1 ? 0xFF79D878 : menu.status() >= 4 ? 0xFFE57C72 : 0xFFB6C0BA;
        graphics.drawString(font, menu.statusText(), 12, 144, color, false);
    }

    private Component toolName() {
        return Component.translatable("ee_extras.pcb.tool." + menu.tool());
    }
}
