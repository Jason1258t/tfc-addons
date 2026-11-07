package ee.extras.pcb;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Пояснения дополняют штатный валидатор, не разрешая сборку вместо него. */
final class PcbDiagnostics {
    record Issue(int layer, int cell, String message) {}
    private PcbDiagnostics() {}
    static List<Issue> check(CompoundTag topology) {
        var issues = new ArrayList<Issue>();
        if (topology.getInt("Version") != PcbTopology.VERSION) return List.of(new Issue(-1, -1, PcbTopology.UNSUPPORTED));
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        for (int index = 0; index < definitions.length && issues.size() < 256; index++) {
            int definition = definitions[index];
            if (definition == 0) continue;
            int layer = index / PcbTopology.CELLS, cell = index % PcbTopology.CELLS;
            if (!PcbWiring.valid(definition)) {
                issues.add(new Issue(layer, cell, "Неизвестный тип или настройки модуля")); continue;
            }
            var module = PcbModules.get(definition);
            if (module.level() > PcbTopology.level(topology)) issues.add(new Issue(layer, cell, module.name() + ": требуется L" + module.level()));
            if (!PcbFootprint.canPlace(topology, layer, cell, definition, index))
                issues.add(new Issue(layer, cell, module.name() + ": корпус пересекает край, вырез, дорожку или другой модуль"));
            if (!PcbParameters.valid(definition, PcbParameters.get(topology, index)))
                issues.add(new Issue(layer, cell, module.name() + ": неверный номинал или порог"));
            for (int pin = 0; pin < module.pins() && issues.size() < 256; pin++) {
                int pad = PcbWiring.pinCell(index, definition, pin);
                if (pad < 0) issues.add(new Issue(layer, cell, module.name() + ": вывод " + module.pinName(pin) + " за краем платы"));
                else if (!PcbWiring.conductor(PcbTopology.get(topology, layer, pad % PcbTopology.CELLS)) || PcbTopology.cut(topology, pad % PcbTopology.CELLS))
                    issues.add(new Issue(layer, pad % PcbTopology.CELLS, module.name() + ": не подключён вывод " + module.pinName(pin)));
            }
        }
        for (int layer = 0; layer < PcbTopology.level(topology) && issues.size() < 256; layer++) {
            for (int cell = 0; cell < PcbTopology.CELLS && issues.size() < 256; cell++) {
                if (PcbTopology.get(topology, layer, cell) != PcbTopology.TERMINAL) continue;
                int x = cell % 16, y = cell / 16;
                if (layer != 0 || x != 0 && x != 15 && y != 0 && y != 15)
                    issues.add(new Issue(layer, cell, "Внешний вывод должен быть на краю монтажной стороны"));
            }
        }
        String error = PcbTopology.validate(topology);
        if (issues.isEmpty() && error != null) {
            int first = -1;
            for (int i = 0; i < definitions.length; i++) if (definitions[i] != 0) { first = i; break; }
            issues.add(new Issue(first < 0 ? -1 : first / PcbTopology.CELLS, first < 0 ? -1 : first % PcbTopology.CELLS, error));
        }
        return List.copyOf(issues);
    }
}
