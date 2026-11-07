package ee.extras.pcb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Числовые параметры экземпляра: единицы совпадают с полями редактора. */
final class PcbParameters {
    static final String KEY = "ModuleParameters";
    static final String ERROR = "Проверьте числовые параметры модуля";
    private PcbParameters() {}

    static CompoundTag get(CompoundTag topology, int index) {
        return topology.getCompound(KEY).getCompound(Integer.toString(index));
    }

    static void put(CompoundTag topology, int index, CompoundTag parameters) {
        CompoundTag all = topology.getCompound(KEY);
        if (parameters.isEmpty()) all.remove(Integer.toString(index));
        else all.put(Integer.toString(index), parameters.copy());
        if (all.isEmpty()) topology.remove(KEY); else topology.put(KEY, all);
    }

    static double nominal(int definition, CompoundTag parameters) {
        if (parameters.contains("Nominal", Tag.TAG_DOUBLE)) return parameters.getDouble("Nominal");
        var module = PcbModules.get(definition);
        double value = PcbWiring.OHMS[PcbWiring.resistance(definition)];
        if (module.timed()) return PcbWiring.period(PcbWiring.type(definition), PcbWiring.resistance(definition)) / 20.0;
        return module.kind().led() || module.kind() == PcbCircuitDevice.Kind.LAMP ? PcbCircuitDevice.indicatorResistance(value) : value;
    }

    static double threshold(int definition, CompoundTag parameters) {
        if (parameters.contains("Threshold", Tag.TAG_DOUBLE)) return parameters.getDouble("Threshold");
        double value = PcbWiring.VOLTS[PcbWiring.threshold(definition)];
        return amps(PcbModules.get(definition)) ? value / 100 : value;
    }

    static boolean amps(PcbModules.Module module) {
        return module.kind() == PcbCircuitDevice.Kind.FUSE || module.kind() == PcbCircuitDevice.Kind.AMMETER;
    }

    static double minimum(PcbModules.Module module) {
        if (module.timed()) return module.kind() == PcbCircuitDevice.Kind.CLOCK ? 0.1 : 1;
        return module.kind().led() || module.kind() == PcbCircuitDevice.Kind.LAMP ? 220 : 0.01;
    }

    static double maximum(PcbModules.Module module) { return module.timed() ? 600 : 1e9; }
    static double maxThreshold(PcbModules.Module module) { return amps(module) ? 100 : 10000; }
    static String unit(PcbModules.Module module) {
        return module.timed() ? "с" : module.kind() == PcbCircuitDevice.Kind.CAPACITOR ? "мФ" : "Ом";
    }

    static boolean valid(int definition, CompoundTag parameters) {
        var module = PcbModules.get(definition);
        for (String name : parameters.getAllKeys()) {
            if (!parameters.contains(name, Tag.TAG_DOUBLE)) return false;
            double value = parameters.getDouble(name);
            if (!Double.isFinite(value)) return false;
            if (name.equals("Nominal")) {
                if (!module.resistanceSetting() || value < minimum(module) || value > maximum(module)) return false;
                if (module.timed() && Math.abs(value * 20 - Math.rint(value * 20)) > 1e-6) return false;
            } else if (name.equals("Threshold")) {
                if (!module.thresholdSetting() || value <= 0 || value > maxThreshold(module)) return false;
            } else return false;
        }
        return true;
    }

    static boolean valid(CompoundTag topology) {
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        CompoundTag parameters = topology.getCompound(KEY);
        if (topology.contains(KEY) && !topology.contains(KEY, Tag.TAG_COMPOUND)) return false;
        for (String key : parameters.getAllKeys()) {
            try {
                int index = Integer.parseInt(key);
                if (!key.equals(Integer.toString(index)) || index < 0 || index >= definitions.length
                        || !PcbWiring.valid(definitions[index]) || !parameters.contains(key, Tag.TAG_COMPOUND)
                        || !valid(definitions[index], parameters.getCompound(key))) return false;
            } catch (NumberFormatException ignored) { return false; }
        }
        return true;
    }
}
