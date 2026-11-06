package ee.extras.wire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Узлы ответвлений декоративного провода в измерении. Узел — по узлу EE на жилу, без блока:
 * InfrastructureSavedData.createDetachedNode(FIXED, pos) — EE 1.1.2 так ставит конец висячего провода; FIXED не
 * удаляется сам при пустой смежности и сохраняется с миром (проверено по байткоду). Здесь же — UUID кусков провода,
 * сходящихся в узле: число известно и для выгруженных чанков, поэтому срастание и снятие узла не ошибаются.
 *
 * В Power Grid 0.6.2 (JunctionWireEndpoint) узлы и куски хранились в памяти и собирались заново при загрузке.
 */
public class JunctionData extends SavedData {
    static final String NAME = "ee_extras_wire_junctions";

    static final class Entry {
        final Vec3 pos;
        final List<InWorldNode> nodes;
        final Set<UUID> wires = new LinkedHashSet<>();

        Entry(Vec3 pos, List<InWorldNode> nodes) {
            this.pos = pos;
            this.nodes = nodes;
        }
    }

    final Map<UUID, Entry> junctions = new HashMap<>();

    public static JunctionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(JunctionData::new, JunctionData::load, null), NAME);
    }

    public WireEnd.Junction create(ServerLevel level, Vec3 pos, int conductors) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        List<InWorldNode> nodes = new ArrayList<>();
        for (int k = 0; k < conductors; k++) {
            InWorldNodeData n = sd.createDetachedNode(DetachedNodeType.FIXED, pos);
            nodes.add(n.node);
        }
        UUID id = UUID.randomUUID();
        junctions.put(id, new Entry(pos, nodes));
        setDirty();
        return new WireEnd.Junction(id, pos);
    }

    public boolean exists(UUID id) {
        return junctions.containsKey(id);
    }

    public List<InWorldNode> nodes(UUID id) {
        Entry e = junctions.get(id);
        return e == null ? null : e.nodes;
    }

    public int count() {
        return junctions.size();
    }

    Set<UUID> wires(UUID id) {
        Entry e = junctions.get(id);
        return e == null ? Set.of() : e.wires;
    }

    void attach(UUID id, UUID wire) {
        Entry e = junctions.get(id);
        if (e != null && e.wires.add(wire)) setDirty();
    }

    /** Кусок from в узле заменён куском to (провод разделили) — без срастания */
    void replace(UUID id, UUID from, UUID to) {
        Entry e = junctions.get(id);
        if (e == null) return;
        e.wires.remove(from);
        e.wires.add(to);
        setDirty();
    }

    /**
     * Кусок ушёл из узла: оставшиеся два срастаются в один (если оба загружены), один — становится свободным краем.
     * Запись убирается из карты до действий с кусками: их собственные detach при этом ничего не делают.
     */
    void detach(ServerLevel level, UUID id, UUID wire) {
        Entry e = junctions.get(id);
        if (e == null || !e.wires.remove(wire)) return;
        setDirty();
        if (e.wires.size() > 2) return;
        WireEnd.Junction j = new WireEnd.Junction(id, e.pos);
        List<UUID> rest = List.copyOf(e.wires);
        junctions.remove(id);
        if (rest.size() == 2) {
            if (!SurfaceWireEntity.mergeAt(level, j, rest)) {
                junctions.put(id, e);
                return;
            }
        } else {
            for (UUID r : rest) SurfaceWireEntity.junctionLost(level, r, j);
        }
        removeNodes(level, e);
    }

    /** Узлы EE узла — вместе с подключёнными к ним жилами (выгруженный кусок узнает об этом при загрузке) */
    static void removeNodes(ServerLevel level, Entry e) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        for (InWorldNode n : e.nodes) {
            if (!sd.hasNode(n)) continue;
            for (var c : sd.getConnections(n)) sd.removeConnectionNoDrops(c);
            sd.removeNode(n);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        junctions.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putDouble("X", e.pos.x);
            t.putDouble("Y", e.pos.y);
            t.putDouble("Z", e.pos.z);
            ListTag nodes = new ListTag();
            for (InWorldNode n : e.nodes) {
                CompoundTag nt = new CompoundTag();
                nt.putInt("Id", n.id());
                nt.put("Pos", NbtUtils.writeBlockPos(n.sourcePos()));
                nodes.add(nt);
            }
            t.put("Nodes", nodes);
            ListTag wires = new ListTag();
            for (UUID w : e.wires) wires.add(NbtUtils.createUUID(w));
            t.put("Wires", wires);
            list.add(t);
        });
        tag.put("Junctions", list);
        return tag;
    }

    static JunctionData load(CompoundTag tag, HolderLookup.Provider registries) {
        JunctionData d = new JunctionData();
        for (Tag el : tag.getList("Junctions", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) el;
            List<InWorldNode> nodes = new ArrayList<>();
            for (Tag nt : t.getList("Nodes", Tag.TAG_COMPOUND)) {
                CompoundTag n = (CompoundTag) nt;
                nodes.add(new InWorldNode(n.getInt("Id"), NbtUtils.readBlockPos(n, "Pos").orElse(BlockPos.ZERO)));
            }
            Entry e = new Entry(new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")), nodes);
            for (Tag w : t.getList("Wires", Tag.TAG_INT_ARRAY)) e.wires.add(NbtUtils.loadUUID(w));
            d.junctions.put(t.getUUID("Id"), e);
        }
        return d;
    }
}
