package ee.extras.pcb;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Параметрический стабилизатор: балласт 150 Ом, шунт 5 В; общий возврат входа и выхода. */
final class PcbVoltageRegulator {
    private static final double BALLAST=150, RETURN=.01, OPEN=1e9, SHUNT_SENSE=.01;
    private final BlockPos pos;
    private final int base;
    private final PcbRegulatorShunt shunt=new PcbRegulatorShunt();
    private double temperature=20, inputWatts,outputWatts,lossWatts,minOutput,maxOutput,inputAmps,maxInput;
    private boolean tripped;
    PcbVoltageRegulator(BlockPos pos,int index){this.pos=pos;this.base=12000+index*3;}
    void preTick(BridgeCollector collector,PcbCircuitDevice.Part p){
        shunt.converged=true;shunt.iterations=0;shunt.residual=0;
        var b=collector.builder(pos);
        // CircuitBuilder.connect заменяет параллельное ребро. Собственные промежуточные узлы не дают
        // регистру или второму модулю затереть балласт/шунт/возврат при общих внешних дорожках.
        double half=(tripped?OPEN:BALLAST)/2;
        b.node(base).resistor(p.a(),base,half).resistor(base,p.coilA(),half);
        if(p.b()!=p.coilB())b.node(base+2).resistor(p.b(),base+2,RETURN/2).resistor(base+2,p.coilB(),RETURN/2);
        if(p.coilA()!=p.coilB())b.node(base+1).connect(p.coilB(),base+1,shunt).resistor(base+1,p.coilA(),SHUNT_SENSE);
    }
    PcbBridgeRectifier.Measurement measure(SimulationResults results,PcbCircuitDevice.Part p){
        double[] a=results.getVoltages(new InWorldNode(p.a(),pos)),b=results.getVoltages(new InWorldNode(p.b(),pos)),c=results.getVoltages(new InWorldNode(p.coilA(),pos)),d=results.getVoltages(new InWorldNode(p.coilB(),pos));
        double[] sense=p.coilA()==p.coilB()?c:results.getVoltages(new InWorldNode(base+1,pos));
        inputWatts=outputWatts=lossWatts=inputAmps=maxInput=0;minOutput=Double.POSITIVE_INFINITY;maxOutput=Double.NEGATIVE_INFINITY;
        double resistance=tripped?OPEN:BALLAST,maxCurrent=0;
        for(int i=0;i<a.length;i++){
            double input=(a[i]-c[i])/resistance,ground=(d[i]-b[i])/RETURN;
            double v=c[i]-d[i],z=(c[i]-sense[i])/SHUNT_SENSE;
            inputWatts+=(a[i]-b[i])*input;outputWatts+=v*(input-z);
            lossWatts+=input*input*resistance+ground*ground*RETURN+(sense[i]-d[i])*z+z*z*SHUNT_SENSE;
            maxCurrent=Math.max(maxCurrent,Math.max(Math.abs(input-z),Math.max(Math.abs(ground),Math.abs(z))));
            inputAmps=Math.max(inputAmps,Math.abs(input));maxInput=Math.max(maxInput,Math.abs(a[i]-b[i]));
            minOutput=Math.min(minOutput,v);maxOutput=Math.max(maxOutput,v);
        }
        if(a.length>0){inputWatts/=a.length;outputWatts/=a.length;lossWatts/=a.length;}else minOutput=maxOutput=0;
        // Теплоёмкость 2 Дж/К, теплоотдача 0,02 Вт/К; выключение 90°C, повторное включение60°C.
        temperature+=.05*(lossWatts-.02*(temperature-20))/2;
        if(!Double.isFinite(temperature))temperature=90;
        if(temperature>=90||!shunt.converged)tripped=true;else if(temperature<=60)tripped=false;
        return new PcbBridgeRectifier.Measurement(lossWatts,shunt.converged?Math.max(maxInput,Math.max(Math.abs(minOutput),Math.abs(maxOutput))):Double.POSITIVE_INFINITY,Math.max(inputAmps,maxCurrent));
    }
    void write(CompoundTag t){t.putDouble("Temperature",temperature);t.putBoolean("Tripped",tripped);t.putDouble("InputWatts",inputWatts);t.putDouble("OutputWatts",outputWatts);t.putDouble("LossWatts",lossWatts);t.putDouble("MinOutput",minOutput);t.putDouble("MaxOutput",maxOutput);t.putDouble("InputAmps",inputAmps);t.putDouble("MaxInput",maxInput);t.putBoolean("Converged",shunt.converged);t.putInt("Iterations",shunt.iterations);}
    String status(){return String.format(java.util.Locale.ROOT,"Стабилизатор 5 В: выход %.3f–%.3f В; вход %.2f В; потери %.3f Вт; %.1f °C%s. Вход 8–12 В DC, нагрузка до 15 мА",minOutput,maxOutput,maxInput,lossWatts,temperature,tripped?" · тепловая защита":"");}
    void read(CompoundTag t){double v=t.getDouble("Temperature");temperature=Double.isFinite(v)?Math.clamp(v,20,200):90;tripped=t.getBoolean("Tripped")||temperature>=90;}
}
