package ee.extras.pcb;

import ee.extras.cable.CableEnd;
import ee.extras.cable.CableKind;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Первичка бортового БП отделена от пользовательских клемм и дорожек платы. */
public final class PcbPowerInlet {
    public static final int BASE = 0x2000;
    public static final int INPUT_A = BASE + 1, INPUT_B = BASE + 2;
    static final String LIMIT = "На плате допускается один бортовой блок питания";
    private PcbPowerInlet() {}

    static int module(net.minecraft.nbt.CompoundTag topology) {
        if (topology == null || topology.getInt("Version") != PcbTopology.VERSION) return -1;
        int result = -1;
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        for (int index = 0; index < definitions.length; index++) {
            if (!PcbWiring.valid(definitions[index]) || !PcbModules.get(definitions[index]).kind().supply()) continue;
            if (result >= 0) return -1;
            result = index;
        }
        return result;
    }

    public static CableEnd cableEnd(Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof PcbCircuitBlock && state.getValue(PcbCircuitBlock.CUSTOM)
                && level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board && module(board.topology()) >= 0
                ? new CableEnd(pos, CableKind.DUPLEX, false, BASE) : null;
    }

    public static boolean hit(Level level, BlockPos pos, BlockState state, Vec3 worldHit) {
        if (cableEnd(level,pos,state) == null || !(level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board)) return false;
        int cell = PcbCircuitBlock.hitCell(worldHit.subtract(Vec3.atLowerCornerOf(pos)), state.getValue(PcbCircuitBlock.FACING));
        return cell >= 0 && PcbFootprint.owner(board.topology(),0,cell) == module(board.topology());
    }

    static void addNodes(PcbCircuitBlockEntity board, BlockState state, Map<Integer, Vec3> nodes) {
        int anchor = module(board.topology());
        if (anchor < 0) return;
        int definition = board.topology().getIntArray(PcbWiring.KEY)[anchor];
        for (int k = 0; k < 3; k++) {
            double x = PcbFootprint.baseWidth(definition) * .5 + (k == 1 ? -.3 : k == 2 ? .3 : 0);
            double z = PcbFootprint.baseHeight(definition) * .5;
            int w = PcbFootprint.baseWidth(definition), h = PcbFootprint.baseHeight(definition);
            if (PcbWiring.mirrored(definition)) x = w - x;
            for (int turn = 0; turn < PcbWiring.rotation(definition); turn++) {
                double nextX = h - z; z = x; x = nextX;
                int nextWidth = h; h = w; w = nextWidth;
            }
            Vec3 local = new Vec3((anchor % 16 + x) / 16, 2.5/16, (anchor / 16 + z) / 16);
            nodes.put(BASE + k, PcbCircuitBlock.mountedPoint(local, state.getValue(PcbCircuitBlock.FACING)));
        }
    }
}
