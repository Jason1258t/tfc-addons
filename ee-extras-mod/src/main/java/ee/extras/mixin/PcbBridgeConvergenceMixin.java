package ee.extras.mixin;

import com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.simulator.Network;
import com.george_vi.electroenergetics.simulation.simulator.SimulationNode;
import com.george_vi.electroenergetics.simulation.util.CholeskySolver;
import com.george_vi.electroenergetics.simulation.util.DataPacker;
import com.george_vi.electroenergetics.simulation.util.LUSolver;
import ee.extras.pcb.PcbBridgeDiode;
import ee.extras.pcb.PcbRegulatorShunt;
import ee.extras.pcb.PcbBridgeDiode.Entry;
import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** EE 1.1.2 делает один шаг Ньютона на отсчёт. Мосту нужна сходимость в текущем отсчёте, а не задержка на AC. */
@Mixin(value=Network.class,remap=false)
public abstract class PcbBridgeConvergenceMixin {
    @Shadow @Final private CircuitBuilder builder;
    @Shadow @Final private Set<com.george_vi.electroenergetics.simulation.WrappedIndexedNode> allNodes;
    @Unique private List<Entry> eeExtras$diodes=List.of();
    @Unique private boolean eeExtras$iterating;

    @Inject(method="mapToSimNodes",at=@At("RETURN"),require=1)
    private void eeExtras$findBridge(CallbackInfoReturnable<SimulationNode[]> ci) {
        var ordinals=new HashSet<Integer>();for(var node:allNodes)ordinals.add(node.ordinal);
        var entries=new ArrayList<Entry>();boolean bridge=false;
        for(var entry:builder.microTickers.long2ObjectEntrySet()) if(entry.getValue() instanceof DiodeProperties diode && ordinals.contains(DataPacker.unpackFirstI(entry.getLongKey()))) {
            entries.add(new Entry(entry.getLongKey(),diode));bridge|=diode instanceof PcbBridgeDiode;
        }
        eeExtras$diodes=bridge?List.copyOf(entries):List.of();
    }
    @Inject(method="getResults",at=@At("RETURN"),require=1)
    private void eeExtras$converge(double[] solution,double[] voltages,int microTick,int count,CallbackInfo ci) {
        if(eeExtras$iterating||eeExtras$diodes.isEmpty())return;
        var network=(Network)(Object)this;eeExtras$iterating=true;
        try {
            boolean converged=false;int used=0;double residual=0;
            int limit=eeExtras$diodes.stream().anyMatch(e->e.diode() instanceof PcbRegulatorShunt)?256:64;
            for(int iteration=0;iteration<limit;iteration++) {
                used=iteration+1;double error=0;
                for(var entry:eeExtras$diodes) {
                    int a=DataPacker.unpackFirstI(entry.nodes()),b=DataPacker.unpackSecondI(entry.nodes());
                    boolean shunt=entry.diode() instanceof PcbRegulatorShunt;
                    double lo=shunt?-512:-.8,hi=shunt?512:.8,step=shunt?1:.1;
                    double actual=Math.clamp(voltages[b*count+microTick]-voltages[a*count+microTick],lo,hi);
                    double previous=Math.clamp(entry.diode().lastVoltage,lo,hi),delta=actual-previous;
                    error=Math.max(error,Math.abs(delta));
                    entry.diode().lastVoltage=previous+Math.clamp(delta*.5,-step,step);
                    entry.diode().tick(voltages,microTick,count,a,b);
                }
                residual=error;
                if(error<1e-6){converged=true;break;}
                // Ёмкости и источники НЕ тикают повторно: время и заряд не продвигаются внутри Ньютона.
                network.formMatrix();
                double[] next=network.voltageSourcesInMatrix?LUSolver.solve(network.conductanceMatrix,network.rhsVector):CholeskySolver.solve(network.conductanceMatrix,network.rhsVector);
                System.arraycopy(next,0,solution,0,solution.length);network.getResults(solution,voltages,microTick,count);
            }
        for(var entry:eeExtras$diodes)if(entry.diode() instanceof PcbBridgeDiode diode){diode.converged&=converged;diode.iterations=Math.max(diode.iterations,used);diode.residual=Math.max(diode.residual,residual);}
        } finally {eeExtras$iterating=false;}
    }
}
