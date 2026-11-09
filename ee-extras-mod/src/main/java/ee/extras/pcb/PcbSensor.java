package ee.extras.pcb;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Питаемый резистивный преобразователь: никаких независимых источников напряжения. */
final class PcbSensor {
    private static final double DIVIDER=10000, DRIVER=100, OPEN=1e9;
    private final Level level;
    private final BlockPos pos;
    private final int base;
    private boolean powered, temperature, rotation, readingValid;
    private double reading, fraction, upper=OPEN, lower=DIVIDER, output, supply, watts, amps, inputWatts, outputWatts, supplyRms, outputRms;
    private String source="нет данных";
    PcbSensor(Level level,BlockPos pos,int index){this.level=level;this.pos=pos;this.base=13000+3*index;}
    void preTick(BridgeCollector collector,PcbCircuitDevice.Part p){
        readingValid=true;
        temperature=p.kind()==PcbCircuitDevice.Kind.TEMPERATURE_SENSOR;
        var face=level.getBlockState(pos).getValue(PcbCircuitBlock.FACING);
        rotation=p.kind()==PcbCircuitDevice.Kind.ROTATION_SENSOR;
        if(rotation){
            BlockPos target=pos.relative(face.getOpposite());
            if(level.isLoaded(target) && level.getBlockEntity(target) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic){
                reading=kinetic.getSpeed();source="механизм Create за платой";
            }else{reading=0;readingValid=false;source="нет механизма Create";}
            fraction=.5+.4*Math.clamp(reading/256,-1,1);
        }else if(temperature){
            if(ModList.get().isLoaded("tfc")) {
                BlockPos target=pos.relative(face.getOpposite());
                var heat=level.isLoaded(target)?Tfc.heat(level,target,face):null;
                reading=heat==null?Tfc.air(level,pos):heat;
                source=heat==null?"воздух TFC":"блок за платой";
            }else {reading=0;readingValid=false;source="TFC недоступен";}
            fraction=.1+.8*Math.clamp((reading+40)/200,0,1);
        }else{
            BlockPos target=pos.relative(face);
            reading=level.isLoaded(target)?level.getMaxLocalRawBrightness(target):0;
            source="свет перед платой";
            fraction=.1+.8*Math.clamp(reading/15,0,1);
        }
        if(!Double.isFinite(reading)){reading=0;fraction=.1;readingValid=false;source="ошибка измерения";}
        if(!readingValid)powered=false;
        upper=powered?DIVIDER*(1-fraction):OPEN;
        lower=powered?DIVIDER*fraction:DIVIDER;
        var b=collector.builder(pos);
        // Каждое ребро имеет собственный узел: параллельные нагрузки EE не затирают делитель.
        b.node(base).node(base+1).node(base+2)
            .resistor(p.a(),base+1,upper/2).resistor(base+1,base,upper/2)
            .resistor(base,base+2,lower/2).resistor(base+2,p.b(),lower/2)
            .resistor(base,p.coilA(),DRIVER);
    }
    PcbBridgeRectifier.Measurement measure(SimulationResults r,PcbCircuitDevice.Part p){
        double[] a=r.getVoltages(new InWorldNode(p.a(),pos)),b=r.getVoltages(new InWorldNode(p.b(),pos)),c=r.getVoltages(new InWorldNode(base,pos)),o=r.getVoltages(new InWorldNode(p.coilA(),pos));
        output=supply=watts=amps=inputWatts=outputWatts=supplyRms=outputRms=0;boolean valid=a.length>0;
        for(int i=0;i<a.length;i++){
            double v=a[i]-b[i],up=a[i]-c[i],down=c[i]-b[i],out=c[i]-o[i];
            valid&=Double.isFinite(v)&&v>=3&&v<=6;
            supply+=v;output+=o[i]-b[i];supplyRms+=v*v;outputRms+=(o[i]-b[i])*(o[i]-b[i]);
            double drive=DRIVER;
            inputWatts+=v*up/upper;outputWatts+=(o[i]-b[i])*out/drive;
            watts+=up*up/upper+down*down/lower+out*out/drive;
            amps=Math.max(amps,Math.max(Math.abs(up/upper),Math.max(Math.abs(down/lower),Math.abs(out/drive))));
        }
        if(a.length>0){supply/=a.length;output/=a.length;watts/=a.length;inputWatts/=a.length;outputWatts/=a.length;supplyRms=Math.sqrt(supplyRms/a.length);outputRms=Math.sqrt(outputRms/a.length);}
        powered=valid&&readingValid;
        return new PcbBridgeRectifier.Measurement(watts,Double.isFinite(watts)&&Double.isFinite(supplyRms)&&Double.isFinite(outputRms)?Math.max(supplyRms,outputRms):Double.POSITIVE_INFINITY,amps);
    }
    void write(CompoundTag t){t.putDouble("SupplyRms",supplyRms);t.putDouble("OutputRms",outputRms);t.putDouble("InputWatts",inputWatts);t.putDouble("OutputWatts",outputWatts);t.putDouble("Reading",reading);t.putDouble("Fraction",fraction);t.putDouble("Supply",supply);t.putDouble("Output",output);t.putDouble("Watts",watts);t.putDouble("Amps",amps);t.putBoolean("Powered",powered);t.putString("Source",source);t.putDouble("OutputResistance",DRIVER+upper*lower/(upper+lower));}
    String status(){
        String target=source.equals("воздух TFC")?"воздух":source.equals("блок за платой")?"блок":"нет данных";
        String label=rotation?String.format(java.util.Locale.ROOT,"Вращение %+.1f об/мин (%s)",reading,source):temperature?String.format(java.util.Locale.ROOT,"Темп. %.1f°C (%s)",reading,target):"Свет "+Math.round(reading)+"/15";
        return String.format(java.util.Locale.ROOT,"%s · А %.3f В · П %.3f В%s",label,output,supply,powered?"":" · отключён");
    }
    /** Класс загружается только при установленном TFC. */
    private static final class Tfc {
        static Float heat(Level level,BlockPos pos,net.minecraft.core.Direction side){var h=level.getCapability(net.dries007.tfc.common.capabilities.BlockCapabilities.HEAT,pos,side);return h==null?null:h.getTemperature();}
        static float air(Level level,BlockPos pos){return net.dries007.tfc.util.climate.Climate.getInstantTemperature(level,pos);}
    }
}
