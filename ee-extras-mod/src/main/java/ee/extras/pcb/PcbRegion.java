package ee.extras.pcb;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/** Атомарный перенос участка со всеми слоями, корпусами, номиналами и ручными разрывами. */
final class PcbRegion {
    static final String ERROR = "Участок не помещается, пересекает детали или обрезает корпус";
    private PcbRegion() {}

    static boolean selection(CompoundTag tag, int first, int last) {
        if (first < 0 || last < 0 || first >= 256 || last >= 256 || tag.getInt("Version") != PcbTopology.VERSION) return false;
        int x0 = Math.min(first % 16, last % 16), x1 = Math.max(first % 16, last % 16);
        int y0 = Math.min(first / 16, last / 16), y1 = Math.max(first / 16, last / 16);
        int[] definitions = tag.getIntArray(PcbWiring.KEY);
        for (int index = 0; index < definitions.length; index++) {
            if (!PcbWiring.valid(definitions[index])) continue;
            int x = index % 16, y = index % 256 / 16;
            int right = x + PcbFootprint.width(definitions[index]) - 1;
            int bottom = y + PcbFootprint.height(definitions[index]) - 1;
            if (x <= x1 && right >= x0 && y <= y1 && bottom >= y0
                    && (x < x0 || right > x1 || y < y0 || bottom > y1)) return false;
        }
        return true;
    }

    static CompoundTag apply(CompoundTag current, CompoundTag source, int first, int last, int target, boolean move) {
        if (!selection(source, first, last) || target < 0 || target >= 256
                || current.getInt("Version") != PcbTopology.VERSION || PcbTopology.level(source) > PcbTopology.level(current)
                || move && !current.equals(source)) return null;
        int x0 = Math.min(first % 16, last % 16), x1 = Math.max(first % 16, last % 16);
        int y0 = Math.min(first / 16, last / 16), y1 = Math.max(first / 16, last / 16);
        int dx = target % 16 - x0, dy = target / 16 - y0;
        if (target % 16 + x1 - x0 >= 16 || target / 16 + y1 - y0 >= 16) return null;
        var result = current.copy();
        if (move) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                int cell = y * 16 + x;
                if (PcbTopology.cut(result, cell)) PcbTopology.toggleCut(result, cell);
                for (int layer = 0; layer < PcbTopology.MAX_LAYERS; layer++) PcbTopology.set(result, layer, cell, PcbTopology.EMPTY);
            }
        }
        // Проверяем всю область до первой записи, включая пустые клетки внутри чужих корпусов.
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            int cell = (y + dy) * 16 + x + dx;
            if (PcbTopology.cut(result, cell)) return null;
            for (int layer = 0; layer < PcbTopology.level(result); layer++) {
                if (PcbTopology.get(result, layer, cell) != PcbTopology.EMPTY || PcbFootprint.owner(result, layer, cell) >= 0) return null;
                for (int direction = 0; direction < 4; direction++) if (PcbTopology.edgeCut(result, layer, cell, direction)) return null;
            }
        }
        int[] definitions = Arrays.copyOf(source.getIntArray(PcbWiring.KEY), 768);
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            int oldCell = y * 16 + x, cell = (y + dy) * 16 + x + dx;
            if (PcbTopology.cut(source, oldCell)) PcbTopology.toggleCut(result, cell);
            for (int layer = 0; layer < PcbTopology.level(source); layer++) {
                int value = PcbTopology.get(source, layer, oldCell);
                if (value == PcbTopology.TERMINAL && (layer != 0 || cell % 16 != 0 && cell % 16 != 15 && cell / 16 != 0 && cell / 16 != 15)) return null;
                if (value == PcbTopology.EMPTY) continue;
                int from = layer * 256 + oldCell, to = layer * 256 + cell;
                PcbTopology.set(result, layer, cell, value);
                if (PcbWiring.valid(definitions[from])) {
                    PcbTopology.setPart(result, layer, cell, definitions[from]);
                    PcbParameters.put(result, to, PcbParameters.get(source, from).copy());
                    if (move) PcbModuleIds.put(result, to, PcbModuleIds.get(source, from));
                }
            }
        }
        byte[] manual = source.getByteArray(PcbTopology.EDGES);
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            for (int layer = 0; layer < PcbTopology.level(source); layer++) {
                int from = layer * 256 + y * 16 + x, cell = (y + dy) * 16 + x + dx;
                if (from >= manual.length) continue;
                for (int direction = 0; direction < 4; direction++) {
                    if ((manual[from] & (1 << direction)) != 0) PcbTopology.setEdgeCut(result, layer, cell, direction, true);
                }
            }
        }
        PcbFootprint.rebuildPinCuts(result);
        return PcbFootprint.validate(result) && PcbParameters.valid(result) ? result : null;
    }
}
