package ee.extras.pcb;

import com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Между сторонами передаётся только световой флаг, электрических рёбер нет. */
final class PcbOptocoupler {
    private static final double INPUT=1000, DIVIDER=10000, DRIVER=100, OPEN=1e9;
    private final BlockPos pos;
    private final int base;
    private final DiodeProperties diode=new DiodeProperties();
    private boolean light, powered, driving;
    private double upper=OPEN, lower=DIVIDER, input, inputCurrent, supply, output, watts, inputWatts, supplyWatts, outputWatts, maxCurrent;
    PcbOptocoupler(BlockPos pos,int index){this.pos=pos;this.base=16000+index*5;}
    void preTick(BridgeCollector collector,PcbCircuitDevice.Part p){
        driving=light&&powered;
        upper=driving?100:OPEN;lower=DIVIDER;
        var b=collector.builder(pos);
        b.node(base).node(base+1).node(base+2).node(base+3).node(base+4)
            .resistor(p.a(),base,INPUT).connect(p.b(),base,diode)
            .resistor(p.coilA(),base+2,upper/2).resistor(base+2,base+1,upper/2)
            .resistor(base+1,base+3,lower/2).resistor(base+3,p.coilB(),lower/2)
            .resistor(base+1,base+4,DRIVER/2).resistor(base+4,p.alternate(),DRIVER/2);
    }
    PcbBridgeRectifier.Measurement measure(SimulationResults r,PcbCircuitDevice.Part p){
        double[] a=v(r,p.a()), b=v(r,p.b()), led=v(r,base), plus=v(r,p.coilA()),minus=v(r,p.coilB()),center=v(r,base+1),out=v(r,p.alternate());
        input=inputCurrent=supply=output=watts=inputWatts=supplyWatts=outputWatts=maxCurrent=0;
        boolean valid=a.length>0;double maxV=0,minI=Double.POSITIVE_INFINITY,maxI=0;
        for(int i=0;i<a.length;i++){
            double vi=a[i]-b[i],ii=(a[i]-led[i])/INPUT,vs=plus[i]-minus[i],up=plus[i]-center[i],down=center[i]-minus[i],vo=out[i]-minus[i],drive=(center[i]-out[i])/DRIVER;
            input+=vi;inputCurrent+=ii;supply+=vs;output+=vo;
            minI=Math.min(minI,ii);maxI=Math.max(maxI,ii);
            valid &= Double.isFinite(vs)&&vs>=3&&vs<=6;
            inputWatts+=vi*ii;supplyWatts+=vs*up/upper;outputWatts+=vo*drive;
            watts+=vi*ii+up*up/upper+down*down/lower+drive*drive*DRIVER;
            maxCurrent=Math.max(maxCurrent,Math.max(Math.abs(ii),Math.max(Math.abs(up/upper),Math.max(Math.abs(down/lower),Math.abs(drive)))));
            maxV=Math.max(maxV,Math.max(Math.abs(vi),Math.max(Math.abs(vs),Math.abs(vo))));
        }
        if(a.length>0){int n=a.length;input/=n;inputCurrent/=n;supply/=n;output/=n;watts/=n;inputWatts/=n;supplyWatts/=n;outputWatts/=n;}
        if(minI>=.002&&maxI<=.01)light=true;else if(minI<=.001||maxI>.01)light=false;
        powered=valid;
        return new PcbBridgeRectifier.Measurement(watts,Double.isFinite(watts)?maxV:Double.POSITIVE_INFINITY,maxCurrent);
    }
    private double[] v(SimulationResults r,int node){return r.getVoltages(new InWorldNode(node,pos));}
    void read(CompoundTag t){light=t.getBoolean("Light");powered=false;double saved=t.getDouble("DiodeVoltage");diode.lastVoltage=Double.isFinite(saved)?Math.clamp(saved,-.8,.8):0;}
    void write(CompoundTag t){t.putDouble("DiodeVoltage",diode.lastVoltage);t.putBoolean("Light",light);t.putBoolean("Powered",powered);t.putBoolean("Driving",driving);t.putDouble("Input",input);t.putDouble("InputCurrent",inputCurrent);t.putDouble("Supply",supply);t.putDouble("Output",output);t.putDouble("InputWatts",inputWatts);t.putDouble("SupplyWatts",supplyWatts);t.putDouble("OutputWatts",outputWatts);t.putDouble("Watts",watts);t.putDouble("Amps",maxCurrent);t.putDouble("OutputResistance",DRIVER+upper*lower/(upper+lower));}
    String status(){return String.format(java.util.Locale.ROOT,"Оптопара · вход %.2f мА · питание выхода %.3f В · выход %.3f В · %s",inputCurrent*1000,supply,output,!powered?"нет питания":light?"свет":"выключена");}
}
