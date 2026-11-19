package ee.extras.lines;

import com.george_vi.electroenergetics.client.ElectricStatsTooltipModifier;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.george_vi.electroenergetics.client.WireRenderer;
import net.minecraft.client.Minecraft;
import net.createmod.catnip.outliner.Outliner;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.george_vi.electroenergetics.client.ElectricStatsTooltipModifier.ElectricStatSet;
import com.simibubi.create.foundation.item.TooltipModifier;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Только клиент: подсказки катушек проводов ЛЭП. Сопротивление, предельный ток (и напряжение у кабеля) — тем же
 * механизмом, что у катушек EE (ElectricStatsTooltipModifier через реестр подсказок Create; у EE его подключает
 * Registrate, наши предметы — без него). Пролёт и провис — своей строкой.
 */
public final class LinesClient {
    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(LinesClient::setup);
        gameBus.addListener(LinesClient::tooltip);
        gameBus.addListener(LinesClient::selection);
    }

    /** Катушкой можно выбрать всю гирлянду. Электрический контакт остаётся на нижнем наконечнике. */
    public static InWorldNode insulatorTip(BlockHitResult hit, InWorldNode original) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.player.getMainHandItem().getItem() instanceof WireSpoolItem)) {
            return original;
        }
        var from = WireInteractionHandler.getTraceOrigin(mc.player);
        double range = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        var to = WireInteractionHandler.getTraceTarget(mc.player, range, from);
        double bestDistance = hit.getType() == HitResult.Type.MISS ? range : from.distanceTo(hit.getLocation());
        if (original != null) {
            var position = original.getPosition(mc.level);
            if (position != null) bestDistance = Math.min(bestDistance, from.distanceTo(position));
        }
        InWorldNode best = original;
        for (var wire : WireRenderer.getAllConnections()) {
            var connection = wire.getFirst();
            if (!InsulatorAnchors.isGlass(wire.getSecond().wireType())) continue;
            var tip = InsulatorAnchors.glassTip(connection);
            var a = connection.node1().getPosition(mc.level);
            var b = connection.node2().getPosition(mc.level);
            if (tip == null || a == null || b == null) continue;
            var points = QuadraticWireHelper.wirePoints(a, b, wire.getSecond().getSag(a.distanceTo(b)), 0.5f, null);
            var previous = a;
            for (int i = 0; i <= points.size(); i++) {
                var next = i == points.size() ? b : points.get(i);
                var intersection = new AABB(previous, next).inflate(0.15).clip(from, to);
                if (intersection.isPresent()) {
                    double distance = from.distanceTo(intersection.get());
                    if (distance < bestDistance) {
                        best = tip;
                        bestDistance = distance;
                    }
                }
                previous = next;
            }
        }
        return best;
    }

    static void selection(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var stack = mc.player.getMainHandItem();
        if (!stack.is(Tension.CLAMP_ITEM.get())) return;
        var selected = stack.get(CEEDataComponents.SELECTED_NODE);
        if (selected == null) return;
        for (var wire : WireRenderer.getAllConnections()) {
            var connection = wire.getFirst();
            if (!InsulatorAnchors.isGlass(wire.getSecond().wireType()) || !connection.isAny(selected)) continue;
            var a = connection.node1().getPosition(mc.level);
            var b = connection.node2().getPosition(mc.level);
            if (a != null && b != null) Outliner.getInstance().showLine("ee_extras_anchor_selected", a, b)
                    .lineWidth(0.08f).colored(0xFFD166).disableLineNormals();
        }
    }

    static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            for (LineWire w : LineWire.values()) {
                Item spool = w.spool.get();
                ElectricStatSet stats = new ElectricStatSet().addResistancePerMeter(() -> w.resistance);
                if (w.maxVoltage > 0) stats.addMaxVoltage(() -> w.maxVoltage);
                stats.addMaxCurrent(() -> w.maxCurrent);
                ElectricStatsTooltipModifier.ALL_ENTRIES.register(spool, stats);
                TooltipModifier.REGISTRY.register(spool, new ElectricStatsTooltipModifier(spool));
            }
        });
    }

    static void tooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        if (Tension.CLAMP_ITEM != null && stack.is(Tension.CLAMP_ITEM.get())) {
            e.getToolTip().add(Component.translatable("item.ee_extras.tension_clamp.tooltip").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (LineWire w : LineWire.values()) {
            if (!stack.is(w.spool.get())) continue;
            e.getToolTip().add(Component.translatable("item.ee_extras." + w.id + "_spool.tooltip").withStyle(ChatFormatting.GRAY));
            Component sag = w.sag == 0 ? Component.translatable("ee_extras.line.rigid")
                    : w.minFactor < 1 ? Component.translatable("ee_extras.line.sag_tension", pct(w.sagPercent()), pct(w.sagPercent() * w.minFactor))
                    : Component.translatable("ee_extras.line.sag", pct(w.sagPercent()));
            e.getToolTip().add(Component.translatable("ee_extras.line.span", w.span).append(" · ").append(sag)
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
    }

    static String pct(double v) {
        return String.format(Locale.ROOT, Math.abs(v - Math.round(v)) < 0.05 ? "%.0f" : "%.1f", v).replace('.', ',');
    }

    private LinesClient() {
    }
}
