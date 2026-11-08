package ee.extras.pcb;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;

/** Четыре штатных диода EE: вход a/b, выход coilA(+)/coilB(−). Энергия приходит только через вход. */
final class PcbBridgeRectifier {
    // Сопротивление выводов 1 Ом на диод; заодно даёт независимое измерение тока.
    static final double SERIES = 1;
    private final PcbBridgeDiode[] diodes = {new PcbBridgeDiode(),new PcbBridgeDiode(),new PcbBridgeDiode(),new PcbBridgeDiode()};
    private double inputWatts,outputWatts,lossWatts,minOutput,maxOutput,outputRms,inputAmps,outputAmps,minInput,maxInput;
    private final BlockPos pos;
    private final int base;
    PcbBridgeRectifier(BlockPos pos,int base) { this.pos=pos;this.base=base; }
    private static int[] anodes(PcbCircuitDevice.Part p) { return new int[]{p.a(),p.b(),p.coilB(),p.coilB()}; }
    private static int[] cathodes(PcbCircuitDevice.Part p) { return new int[]{p.coilA(),p.coilA(),p.a(),p.b()}; }
    void preTick(BridgeCollector collector,PcbCircuitDevice.Part p) {
        for(var diode:diodes){diode.converged=true;diode.iterations=0;diode.residual=0;}
        var builder=collector.builder(pos);var a=anodes(p);var c=cathodes(p);
        for(int i=0;i<4;i++) builder.node(base+i).connect(base+i,a[i],diodes[i]).resistor(base+i,c[i],SERIES);
    }
    record Measurement(double watts,double volts,double amps) {}
    Measurement measure(SimulationResults results,PcbCircuitDevice.Part p) {
        var a=anodes(p);var c=cathodes(p);double watts=0,volts=0,amps=0;
        double[][] currents=new double[4][];
        for(int i=0;i<4;i++) {
            double[] va=results.getVoltages(new InWorldNode(a[i],pos)), vc=results.getVoltages(new InWorldNode(c[i],pos)), vs=results.getVoltages(new InWorldNode(base+i,pos));
            currents[i]=new double[va.length];
            double loss=0,currentSquared=0;
            for(int sample=0;sample<va.length;sample++) { double current=(vs[sample]-vc[sample])/SERIES;currents[i][sample]=current;loss+=(va[sample]-vc[sample])*current;currentSquared+=current*current; }
            if(va.length>0) { watts+=Math.max(0,loss/va.length);amps=Math.max(amps,Math.sqrt(currentSquared/va.length)); }
        }
        int[] pins={p.a(),p.b(),p.coilA(),p.coilB()};
        for(int x:pins) for(int y:pins) volts=Math.max(volts,Math.sqrt(results.getVoltageAtSqr(pos,x,y)));
        double[] viA=results.getVoltages(new InWorldNode(p.a(),pos)),viB=results.getVoltages(new InWorldNode(p.b(),pos)),voA=results.getVoltages(new InWorldNode(p.coilA(),pos)),voB=results.getVoltages(new InWorldNode(p.coilB(),pos));
        double inputSquared=0,outputSquared=0;
        minInput=Double.POSITIVE_INFINITY;maxInput=Double.NEGATIVE_INFINITY;
        inputWatts=outputWatts=outputRms=0;minOutput=Double.POSITIVE_INFINITY;maxOutput=Double.NEGATIVE_INFINITY;
        for(int t=0;t<viA.length;t++) {
            minInput=Math.min(minInput,viA[t]-viB[t]);maxInput=Math.max(maxInput,viA[t]-viB[t]);
            double output=voA[t]-voB[t]; minOutput=Math.min(minOutput,output);maxOutput=Math.max(maxOutput,output);outputRms+=output*output;
            double inputCurrent=currents[0][t]-currents[2][t],outputCurrent=currents[0][t]+currents[1][t];
            inputSquared+=inputCurrent*inputCurrent;outputSquared+=outputCurrent*outputCurrent;
            inputWatts+=(viA[t]-viB[t])*inputCurrent;
            outputWatts+=output*(currents[0][t]+currents[1][t]);
        }
        inputAmps=outputAmps=0;
        if(viA.length>0) {inputAmps=Math.sqrt(inputSquared/viA.length);outputAmps=Math.sqrt(outputSquared/viA.length);inputWatts/=viA.length;outputWatts/=viA.length;outputRms=Math.sqrt(outputRms/viA.length);amps=Math.max(amps,Math.sqrt(Math.max(inputSquared,outputSquared)/viA.length));} else minInput=maxInput=minOutput=maxOutput=0;
        lossWatts=watts;
        if(java.util.Arrays.stream(diodes).anyMatch(d->!d.converged)) volts=Double.POSITIVE_INFINITY;
        return new Measurement(watts,volts,amps);
    }
    void write(net.minecraft.nbt.CompoundTag tag) {
        tag.putBoolean("Converged",java.util.Arrays.stream(diodes).allMatch(d->d.converged));tag.putInt("Iterations",java.util.Arrays.stream(diodes).mapToInt(d->d.iterations).max().orElse(0));
        tag.putDouble("InputWatts",inputWatts);tag.putDouble("OutputWatts",outputWatts);tag.putDouble("LossWatts",lossWatts);
        tag.putDouble("Residual",java.util.Arrays.stream(diodes).mapToDouble(d->d.residual).max().orElse(0));
        tag.putDouble("InputAmps",inputAmps);tag.putDouble("OutputAmps",outputAmps);
        tag.putDouble("MinInput",minInput);tag.putDouble("MaxInput",maxInput);
        tag.putDouble("MinOutput",minOutput);tag.putDouble("MaxOutput",maxOutput);tag.putDouble("OutputRms",outputRms);
    }
}
