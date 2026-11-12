package tfc.destroy.extras.client;

import blusunrize.immersiveengineering.client.gui.IEContainerScreen;
import blusunrize.immersiveengineering.client.gui.info.FluidInfoArea;
import blusunrize.immersiveengineering.client.gui.info.InfoArea;
import blusunrize.immersiveengineering.client.gui.info.TooltipArea;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.IFluidTank;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ElectrolyzerMenu;
import tfc.destroy.extras.electrolyzer.ElectrolyzerStatus;

/** Окно электролизёра: шкалы баков как у ферментера IE (тот же спрайт стекла), прогресс, состояние, питание. */
public final class ElectrolyzerScreen extends IEContainerScreen<ElectrolyzerMenu> {
    private static final ResourceLocation TEXTURE = DestroyExtras.rl("textures/gui/electrolyzer.png");
    private static final ResourceLocation TANK_OVERLAY = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "fermenter/tank_overlay");

    public ElectrolyzerScreen(ElectrolyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
        imageWidth = ElectrolyzerMenu.WIDTH;
        imageHeight = ElectrolyzerMenu.HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected List<InfoArea> makeInfoAreas() {
        return List.of(
            tank(menu.input, 10, "input"),
            tank(menu.mixture, 30, "mixture"),
            tank(menu.cathodeGas, 116, "cathode_gas"),
            tank(menu.anodeGas, 136, "anode_gas"),
            tank(menu.catholyte, 156, "catholyte"),
            new TooltipArea(new Rect2i(leftPos + 53, topPos + 46, 56, 6), () ->
                Component.translatable("gui.destroy_extras.electrolyzer.progress", Math.round(menu.progressFraction() * 100))));
    }

    private FluidInfoArea tank(IFluidTank tank, int x, String role) {
        return new FluidInfoArea(tank, new Rect2i(leftPos + x, topPos + 18, 16, 47), 20, 51, TANK_OVERLAY) {
            @Override
            public void fillTooltipOverArea(int mouseX, int mouseY, List<Component> tooltip) {
                tooltip.add(Component.translatable("gui.destroy_extras.electrolyzer.tank." + role).withStyle(ChatFormatting.GOLD));
                super.fillTooltipOverArea(mouseX, mouseY, tooltip);
            }
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xE0E0E0, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xE0C8A0, true);
        // Прогресс партии — зелёная полоса в рамке под слотом мембраны.
        int filled = Math.round(56 * menu.progressFraction());
        if (filled > 0) graphics.fill(53, 46, 53 + filled, 52, 0xFF4FC24F);
        ElectrolyzerStatus status = menu.status();
        graphics.drawString(font, menu.statusText(), 8, 69, color(status), true);
        String power = String.format(Locale.ROOT, "%.0f В · %.1f А", menu.voltage(), menu.current());
        graphics.drawString(font, power, 8, 79, 0xC8C8C8, true);
        Component membrane = Component.translatable("gui.destroy_extras.electrolyzer.membrane", menu.membraneLeft());
        graphics.drawString(font, membrane, 81 - font.width(membrane) / 2, 55, 0xC8C8C8, true);
    }

    private static int color(ElectrolyzerStatus status) {
        return switch (status) {
            case RUNNING -> 0x6BE36B;
            case IDLE -> 0xC8C8C8;
            case WAITING_FOR_POWER, BLOCKED_OUTPUT -> 0xF0B040;
            case NO_RECIPE, FAULT -> 0xFF6060;
        };
    }
}
