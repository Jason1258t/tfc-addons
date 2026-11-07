package ee.extras.pcb;

import net.minecraft.nbt.CompoundTag;

/** Идентичность сохраняется при настройке и переносе, но меняется при замене детали. */
final class PcbModuleIds {
    static final String KEY = "ModuleIds";
    private PcbModuleIds() {}
    static long get(CompoundTag topology, int index) { return topology.getCompound(KEY).getLong(Integer.toString(index)); }
    static void put(CompoundTag topology, int index, long id) {
        CompoundTag ids = topology.getCompound(KEY);
        if (id == 0) ids.remove(Integer.toString(index)); else ids.putLong(Integer.toString(index), id);
        if (ids.isEmpty()) topology.remove(KEY); else topology.put(KEY, ids);
    }
    static void ensure(CompoundTag topology, int index) {
        if (get(topology, index) != 0) return;
        long id;
        do { id = java.util.concurrent.ThreadLocalRandom.current().nextLong(); } while (id == 0);
        put(topology, index, id);
    }
    static void normalize(CompoundTag source, CompoundTag target) {
        int[] definitions = target.getIntArray(PcbWiring.KEY);
        for (int i = 0; i < definitions.length; i++) {
            if (!PcbWiring.valid(definitions[i])) continue;
            put(target, i, get(source, i));
            ensure(target, i);
        }
    }
}
