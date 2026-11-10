package ee.extras.pcb;

import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Девять независимых металлических контактов без источника и копирования сигналов. */
public final class PcbConnector {
    public static final int BASE=0x10000;
    public static final double OHMS=.01;
    private PcbConnector() {}
    public static int node(int anchor,int pin) { return BASE+anchor*16+pin; }
    public static int anchor(int node) { return (node-BASE)/16; }
    public static int pin(int node) { return (node-BASE)%16; }
    public static boolean contains(int node) { return node>=BASE && node<BASE+PcbTopology.CELLS*16 && pin(node)<9; }
    static void preTick(BridgeCollector collector,PcbCircuitDevice.Part part,BlockPos pos) {
        var builder=collector.builder(pos); int[] pins=part.pins();
        for(int pin=0;pin<9;pin++) builder.resistor(node(part.moduleIndex(),pin),pins[pin],OHMS);
    }
    /** Потери, напряжение относительно общего, наибольшая доля допустимого тока. */
    static double[] measure(SimulationResults results,PcbCircuitDevice.Part part,BlockPos pos) {
        double watts=0,volts=0,ratio=0; int[] pins=part.pins();
        for(int pin=0;pin<9;pin++) {
            double v2=results.getVoltageAtSqr(pos,node(part.moduleIndex(),pin),pins[pin]);
            watts+=v2/OHMS; ratio=Math.max(ratio,Math.sqrt(v2)/OHMS/(pin==8?.08:.01));
            if(pin<8) {
                volts=Math.max(volts,Math.sqrt(results.getVoltageAtSqr(pos,pins[pin],pins[8])));
                volts=Math.max(volts,Math.sqrt(results.getVoltageAtSqr(pos,node(part.moduleIndex(),pin),node(part.moduleIndex(),8))));
            }
        }
        return new double[]{watts,volts,ratio};
    }
    static void addNodes(PcbCircuitBlockEntity board,BlockState state,Map<Integer,Vec3> nodes) {
        if(board.topology()==null) return; int[] definitions=board.topology().getIntArray(PcbWiring.KEY);
        for(int anchor=0;anchor<definitions.length;anchor++) {
            int def=definitions[anchor];
            if(!PcbWiring.valid(def)||PcbModules.get(def).kind()!=PcbCircuitDevice.Kind.CONNECTOR_8) continue;
            for(int pin=0;pin<9;pin++) {
                double x=1,z=pin+.5; int w=2,h=9;
                if(PcbWiring.mirrored(def)) x=w-x;
                for(int r=0;r<PcbWiring.rotation(def);r++) {double nx=h-z;z=x;x=nx;int nw=h;h=w;w=nw;}
                nodes.put(node(anchor,pin),PcbCircuitBlock.mountedPoint(new Vec3((anchor%16+x)/16,2.0/16,(anchor/16+z)/16),state.getValue(PcbCircuitBlock.FACING)));
            }
        }
    }
}
