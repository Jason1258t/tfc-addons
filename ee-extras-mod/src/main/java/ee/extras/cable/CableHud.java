package ee.extras.cable;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireApplyingBehaviour;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireItem;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationState;
import ee.extras.EeExtras;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Только клиент: катушка кабеля в руке. Подсветку узла кабеля и «пунктир» будущего кабеля в EE 1.1.2 рисует
 * BundledWireApplyingBehaviour.tick, но его никто не вызывает (по байткоду — ни одного вызова) — вызываем сами. Подпись
 * узла кабеля — своя, в том же месте и цветах, что подпись узла у EE (ElectricPropertiesOverlay): у EE она только для
 * открытых узлов, а узел кабеля скрыт, чтобы к нему не цепляли обычный провод.
 */
public final class CableHud {
    /** Цвет заголовка подписи узла у EE */
    private static final int TITLE = 0xFBDE7D;

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(CableHud::layers);
        gameBus.addListener(CableHud::tick);
    }

    static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, EeExtras.rl("cable_node"), CableHud::render);
    }

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.player.getMainHandItem().getItem() instanceof BundledWireItem))
            return;
        if (!pcbPreview(mc)) BundledWireApplyingBehaviour.tick();
        // Подпись узла от прошлого предмета (катушки провода) иначе висела бы поверх своей
        ElectricPropertiesOverlay.INSTANCE.removeHoveredNode();
    }

    private static boolean pcbPreview(Minecraft mc) {
        if (mc.player.getMainHandItem().has(CEEDataComponents.SELECTED_NODE)) return false;
        var selected = mc.player.getMainHandItem().get(CEEDataComponents.SELECTED_WIRE_TERMINATION);
        CableEnd first = selected == null ? null : CableEnd.of(mc.level, selected.pos(), mc.level.getBlockState(selected.pos()));
        CableEnd hovered = mc.hitResult instanceof BlockHitResult hit ? CableEnd.of(mc.level, hit.getBlockPos(), mc.level.getBlockState(hit.getBlockPos())) : null;
        if (hovered != null && hovered.base() == ee.extras.pcb.PcbPowerInlet.BASE && selected == null
                && mc.player.getMainHandItem().getItem() instanceof ThinSpoolItem
                && mc.hitResult instanceof BlockHitResult hit
                && !ee.extras.pcb.PcbPowerInlet.hit(mc.level,hit.getBlockPos(),mc.level.getBlockState(hit.getBlockPos()),hit.getLocation())) hovered = null;
        boolean firstPcb = first != null && first.base() == ee.extras.pcb.PcbPowerInlet.BASE;
        boolean hoveredPcb = hovered != null && hovered.base() == ee.extras.pcb.PcbPowerInlet.BASE;
        if (!firstPcb && !hoveredPcb) return false;
        var outliner = net.createmod.catnip.outliner.Outliner.getInstance();
        var kind = mc.player.getMainHandItem().getItem() instanceof FourCoreSpoolItem ? CableKind.FOUR_CORE : CableKind.DUPLEX;
        if (first != null) {
            var point = first.cable().getPosition(mc.level);
            if (point != null) outliner.showAABB("ee_extras_pcb_cable_selected", net.minecraft.world.phys.AABB.ofSize(point,firstPcb?.055:.25,firstPcb?.055:.25,firstPcb?.055:.25),3)
                    .colored(0xE3C968).lineWidth(firstPcb?.003f:.015625f);
        }
        if (hovered != null) {
            var point = hovered.cable().getPosition(mc.level);
            if (point != null) {
                boolean valid = CableLaying.fits(kind,hovered) && (first == null || CableLaying.fits(kind,first) && !first.pos().equals(hovered.pos()));
                if (valid && first != null) {
                    int max = com.george_vi.electroenergetics.config.CEEConfigs.server().maxBundledWireLength.get();
                    valid = first.pos().distSqr(hovered.pos()) <= (double)max*max;
                    if (valid) {
                        var connectionKey = new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(first.cable(),hovered.cable());
                        for (var connection : com.george_vi.electroenergetics.client.WireRenderer.getAllConnections())
                            if (connectionKey.equals(connection.getFirst())) { valid = false; break; }
                    }
                }
                outliner.showAABB("ee_extras_pcb_cable_hover", net.minecraft.world.phys.AABB.ofSize(point,hoveredPcb?.055:.25,hoveredPcb?.055:.25,hoveredPcb?.055:.25),3)
                        .colored(valid ? 0x82CF9D : 0xE77870).lineWidth(hoveredPcb?.003f:.015625f);
                var start = first == null ? null : first.cable().getPosition(mc.level);
                if (start != null && valid) outliner.showLine("ee_extras_pcb_cable_preview",start,point).colored(0xE3C968).lineWidth(.006f);
            }
        }
        return true;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof BundledWireItem) || !(mc.hitResult instanceof BlockHitResult hit)) return;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        CableEnd end = CableEnd.of(mc.level, hit.getBlockPos(), state);
        if (end == null) return;
        if (end.base() == ee.extras.pcb.PcbPowerInlet.BASE && stack.getItem() instanceof ThinSpoolItem
                && (stack.has(CEEDataComponents.SELECTED_NODE) || !stack.has(CEEDataComponents.SELECTED_WIRE_TERMINATION)
                    && !ee.extras.pcb.PcbPowerInlet.hit(mc.level,hit.getBlockPos(),state,hit.getLocation()))) return;

        CableKind spool = stack.getItem() instanceof FourCoreSpoolItem ? CableKind.FOUR_CORE : CableKind.DUPLEX;
        boolean fits = CableLaying.fits(spool, end);
        BundledWireTerminationState selected = stack.get(CEEDataComponents.SELECTED_WIRE_TERMINATION);
        MutableComponent title = (end.base() == ee.extras.pcb.PcbPowerInlet.BASE ? Component.literal("Ввод бортового трансформатора · 300 В~") : end.contacts() == null ? Component.translatable("ee_extras.nodes.cable") : EeExtras.CABLE_CAP.get().getDescription().copy()).withStyle(ChatFormatting.BOLD);
        MutableComponent line;
        if (!fits) line = Component.translatable("ee_extras.cable." + (spool == CableKind.DUPLEX ? "needs_duplex" : "needs_four_core"));
        else if (selected != null && selected.pos().equals(end.pos())) line = Component.translatable("ee_extras.cable.selected");
        else line = Component.translatable("ee_extras.cable.accepts." + (end.eeTermination() ? "ee" : end.kind() == CableKind.DUPLEX ? "duplex" : "four_core"));

        int x = g.guiWidth() / 2, y = g.guiHeight() / 2 + 33;
        g.drawString(mc.font, title, x - mc.font.width(title) / 2, y, fits ? TITLE : ChatFormatting.RED.getColor());
        g.drawString(mc.font, line, x - mc.font.width(line) / 2, y + 12, fits ? 0xFFFFFF : ChatFormatting.RED.getColor());
    }

    private CableHud() {
    }
}
