package ee.extras.lines;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.wire.WireAttachment;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeEntity;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import com.simibubi.create.AllSoundEvents;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Анкер хранится как навесная деталь EE. Узлы и длина проводника остаются штатными. */
public final class InsulatorAnchors {
    private static final double SIN_ANGLE = Math.sin(Math.toRadians(60));
    private static final double MIN_SPAN = 1.5;

    private InsulatorAnchors() {}

    private static boolean isGlass(WireType type) {
        if (type == CEEWireTypes.GLASS_INSULATOR.get()) return true;
        for (var colored : CEEWireTypes.COLORED_GLASS_INSULATOR) {
            if (type == colored.get()) return true;
        }
        return false;
    }

    private static boolean isPhysical(InWorldNodeData node) {
        return node != null && node.detachedNodeType != null && node.detachedNodeType.isPhysics();
    }

    static boolean isHangingSpan(InfrastructureSavedData sd, InWorldNodeConnection span) {
        return isPhysical(sd.getNodeData(span.node1())) && isPhysical(sd.getNodeData(span.node2()));
    }

    private static boolean isAnchor(Pair<Float, WireAttachment> attachment) {
        return attachment.getSecond().type == Tension.ANCHOR_CLAMP.get();
    }

    public static boolean hasAnchor(WireData wire) {
        for (var attachment : wire.attachments()) {
            if (isAnchor(attachment)) return true;
        }
        return false;
    }

    static boolean hasAnchor(WireData wire, boolean first) {
        for (var attachment : wire.attachments()) {
            if (isAnchor(attachment) && (attachment.getFirst() < 0.5f) == first) return true;
        }
        return false;
    }

    private record Suspension(Vec3 top, double length) {
        Vec3 tip(Vec3 direction, double scale) {
            double reach = length * SIN_ANGLE * scale;
            return top.add(direction.scale(reach)).add(0, -Math.sqrt(length * length - reach * reach), 0);
        }
    }

    private record Span(Suspension first, Suspension second, Vec3 direction, double scale) {
        Vec3 tip(boolean atFirst) {
            return atFirst ? first.tip(direction, scale) : second.tip(direction.scale(-1), scale);
        }
    }

    private static Suspension suspension(InfrastructureSavedData sd, InWorldNodeData tip, InWorldNodeData other) {
        if (!isPhysical(tip) || other == null) return null;
        Suspension suspension = null;
        for (var entry : tip.adjacency.int2ObjectEntrySet()) {
            if (entry.getIntKey() == other.id) continue;
            InWorldNodeData neighbor = sd.getNodeData(entry.getIntKey());
            if (neighbor == null || DetachedNodeHelper.isDetached(neighbor.node)) return null;
            // Отводы на неподвижные клеммы нужны, чтобы запитать провод на конце гирлянды.
            WireData wire = entry.getValue();
            if (!isGlass(wire.wireType())) continue;
            if (suspension != null || wire.length <= 0) return null;
            suspension = new Suspension(neighbor.getGlobalPos(), wire.length);
        }
        return suspension;
    }

    private static Span span(InfrastructureSavedData sd, InWorldNodeData first, InWorldNodeData second) {
        Suspension a = suspension(sd, first, second);
        Suspension b = suspension(sd, second, first);
        if (a == null || b == null) return null;
        Vec3 horizontal = b.top.subtract(a.top).multiply(1, 0, 1);
        double distance = horizontal.length();
        if (distance <= MIN_SPAN) return null;
        double scale = Math.min(1, (distance - MIN_SPAN) / ((a.length + b.length) * SIN_ANGLE));
        return new Span(a, b, horizontal.scale(1 / distance), scale);
    }

    static void install(InfrastructureSavedData sd, InWorldNodeConnection connection, WireData wire, boolean first,
                        Player player, ItemStack stack) {
        InWorldNodeData a = sd.getNodeData(connection.node1());
        InWorldNodeData b = sd.getNodeData(connection.node2());
        Span span = span(sd, a, b);
        if (span == null) {
            player.displayClientMessage(Component.translatable("ee_extras.tension.anchor_invalid"), true);
            return;
        }
        if (Tension.hasClamp(wire) || hasAnchor(wire, first)) {
            player.displayClientMessage(Component.translatable("ee_extras.tension.anchor_already"), true);
            return;
        }
        double distance = span.tip(true).distanceTo(span.tip(false));
        float offset = (float) Math.min(0.30, Tension.CLAMP_FROM_END / distance);
        List<Pair<Float, WireAttachment>> attachments = new ArrayList<>(wire.attachments());
        attachments.add(Pair.of(first ? offset : 1 - offset, new WireAttachment(Tension.ANCHOR_CLAMP.get())));
        updateAttachments(sd, connection, wire, attachments);
        AllSoundEvents.WRENCH_ROTATE.playOnServer(sd.level, BlockPos.containing((first ? a : b).getGlobalPos()));
        if (!player.isCreative()) stack.shrink(1);
        player.displayClientMessage(Component.translatable("ee_extras.tension.anchor_done"), true);
    }

    private static void updateAttachments(InfrastructureSavedData sd, InWorldNodeConnection connection,
                                          WireData wire, List<Pair<Float, WireAttachment>> attachments) {
        sd.setConnectionData(connection, new WireData(wire.wireType(), wire.temperature(), attachments, wire.length));
        sd.setDirty();
    }

    /** EE синхронизирует позицию узла по сущности с поправкой +1/8 блока по Y. */
    public static boolean positionTip(InfrastructureSavedData sd, InWorldNodeData tip, DetachedNodeEntity entity) {
        for (var entry : tip.adjacency.int2ObjectEntrySet()) {
            WireData wire = entry.getValue();
            if (!hasAnchor(wire)) continue;
            InWorldNodeData other = sd.getNodeData(entry.getIntKey());
            if (other == null) continue;
            InWorldNodeConnection connection = new InWorldNodeConnection(tip.node, other.node);
            boolean first = connection.node1().equals(tip.node);
            Span span = first ? span(sd, tip, other) : span(sd, other, tip);
            if (span == null) {
                var attachments = wire.attachments().stream().filter(a -> !isAnchor(a)).toList();
                updateAttachments(sd, connection, wire, new ArrayList<>(attachments));
                entity.spawnAtLocation(new ItemStack(Tension.CLAMP_ITEM.get(), wire.attachments().size() - attachments.size()));
                return false;
            }
            if (hasAnchor(wire, first)) {
                Vec3 target = span.tip(first).add(0, -0.125, 0);
                if (entity.position().distanceToSqr(target) > 1e-8) entity.setPos(target);
                entity.setDeltaMovement(Vec3.ZERO);
                return true;
            }
        }
        return false;
    }

    /** Формула провиса EE с натяжением по материалу. Длина в электросети сохраняется для сопротивления и сматывания. */
    public static float sag(WireData wire, double distance) {
        float factor = Tension.minFactor(wire.wireType());
        if (distance <= 0 || Float.isNaN(factor)) return wire.getSag();
        double visualLength = Math.max(0, distance - (1 - factor));
        return (float) (wire.getSag() * visualLength * factor / distance);
    }
}
