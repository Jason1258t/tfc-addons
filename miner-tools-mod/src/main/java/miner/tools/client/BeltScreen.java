package miner.tools.client;

import miner.tools.BeltMenu;
import miner.tools.BeltFilter;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Окно фильтра: фон сундука на один ряд (как у ванильного ContainerScreen) с настройками над образцами. */
public class BeltScreen extends AbstractContainerScreen<BeltMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final BeltFilter.Group[] GROUPS = BeltFilter.Group.values();

    private Button mode, enabled;
    private final Button[] groups = new Button[GROUPS.length];

    public BeltScreen(BeltMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageHeight = 114 + 5 * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        enabled = addRenderableWidget(Button.builder(Component.empty(), b -> press(BeltMenu.BUTTON_ENABLED))
                .bounds(leftPos + 7, topPos + 15, 80, 16)
                .tooltip(Tooltip.create(Component.translatable("miner_belt.gui.enabled.hint"))).build());
        for (int i = 0; i < GROUPS.length; i++) {
            int id = BeltMenu.BUTTON_GROUP + i;
            groups[i] = addRenderableWidget(Button.builder(Component.empty(), b -> press(id))
                    .bounds(leftPos + 7 + (i + 1) % 2 * 82, topPos + 15 + (i + 1) / 2 * 17, 80, 16)
                    .tooltip(Tooltip.create(Component.translatable("miner_belt.gui.group.hint"))).build());
        }
        mode = addRenderableWidget(Button.builder(Component.empty(), b -> press(BeltMenu.BUTTON_MODE))
                .bounds(leftPos + 7, topPos + 67, 162, 17)
                .tooltip(Tooltip.create(Component.translatable("miner_belt.gui.mode.hint"))).build());
        updateLabels();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void updateLabels() {
        enabled.setMessage(Component.translatable(menu.enabled() ? "miner_belt.gui.enabled.on" : "miner_belt.gui.enabled.off"));
        mode.setMessage(Component.translatable(menu.allowsListedItems() ? "miner_belt.gui.allow" : "miner_belt.gui.deny"));
        for (int i = 0; i < GROUPS.length; i++) {
            BeltFilter.Group group = GROUPS[i];
            groups[i].setMessage(Component.literal(menu.blocksGroup(group) ? "[✓] " : "[ ] ")
                    .append(Component.translatable("miner_belt.group." + group.name().toLowerCase(Locale.ROOT))));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateLabels();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, 17);
        for (int row = 0; row < 4; row++) {
            g.blit(BACKGROUND, leftPos, topPos + 17 + row * 18, 0, 126, imageWidth, 18);
        }
        g.blit(BACKGROUND, leftPos, topPos + 89, 0, 17, imageWidth, 18);
        g.blit(BACKGROUND, leftPos, topPos + 107, 0, 126, imageWidth, 96);
    }
}
