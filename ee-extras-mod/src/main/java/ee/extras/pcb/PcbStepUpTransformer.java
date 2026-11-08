package ee.extras.pcb;

import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Изолированная вторичка ×10: повышает только AC, энергия поступает через первичку EE. */
final class PcbStepUpTransformer {
    static final double RATIO = 10, MAX_INPUT = 48, OUTPUT_OHMS = 20000;
    private static final double WINDING_OHMS = .1, BLEED_OHMS = 1e8;
    private final BlockPos pos;
    private final int primarySense, secondarySense, outputLimit;
    private final TransformerBehaviour.TransformerBehaviourDataHolder data = new TransformerBehaviour.TransformerBehaviourDataHolder();
    private boolean enabled, stamped;
    private int cooldown;
    private double overload, inputRms, outputRms, primaryAmps, secondaryAmps, inputWatts, lossWatts;

    PcbStepUpTransformer(BlockPos pos, int index) {
        this.pos = pos;
        primarySense = 10000 + index*3;
        secondarySense = primarySense+1;
        outputLimit = primarySense+2;
    }

    void preTick(BridgeCollector collector, PcbCircuitDevice.Part part) {
        var builder = collector.builder(pos);
        builder.resistor(part.a(),part.b(),BLEED_OHMS);
        stamped = enabled && cooldown == 0;
        if (stamped) {
            TransformerBehaviour.preTick(new InWorldNode[]{node(part.a()),node(part.b()),node(part.coilA()),
                    node(outputLimit),node(primarySense),node(secondarySense)},1/RATIO,pos,collector,data);
            builder.resistor(outputLimit,part.coilB(),OUTPUT_OHMS);
        } else builder.resistor(part.coilA(),part.coilB(),1e9);
    }

    private InWorldNode node(int id) { return new InWorldNode(id,pos); }

    void postTick(SimulationResults results, PcbCircuitDevice.Part part) {
        inputRms = Math.sqrt(results.getVoltageAtSqr(pos,part.a(),part.b()));
        outputRms = Math.sqrt(results.getVoltageAtSqr(pos,part.coilA(),part.coilB()));
        primaryAmps = secondaryAmps = inputWatts = lossWatts = 0;
        if (stamped) {
            primaryAmps = Math.sqrt(results.getVoltageAtSqr(pos,part.a(),primarySense))/WINDING_OHMS;
            secondaryAmps = Math.sqrt(results.getVoltageAtSqr(pos,secondarySense,outputLimit))/WINDING_OHMS;
            lossWatts = WINDING_OHMS*primaryAmps*primaryAmps + (WINDING_OHMS+OUTPUT_OHMS)*secondaryAmps*secondaryAmps;
            double[] a = results.getVoltages(node(part.a())), b = results.getVoltages(node(part.b())), sense = results.getVoltages(node(primarySense));
            int count = Math.min(a.length,Math.min(b.length,sense.length));
            for (int n=0;n<count;n++) inputWatts += (a[n]-b[n])*(a[n]-sense[n])/WINDING_OHMS;
            if (count>0) inputWatts /= count;
            double[] outputA=results.getVoltages(node(part.coilA())), outputB=results.getVoltages(node(part.coilB()));
            double[] returnEnd=results.getVoltages(node(outputLimit)), returnSense=results.getVoltages(node(secondarySense));
            int outputs=Math.min(Math.min(outputA.length,outputB.length),Math.min(returnEnd.length,returnSense.length));
            double outputWatts=0;
            for (int n=0;n<outputs;n++) outputWatts+=(outputA[n]-outputB[n])*(returnEnd[n]-returnSense[n])/WINDING_OHMS;
            if (outputs>0) outputWatts/=outputs;
            // EE может исключить регуляризующие сопротивления при оптимизации связанной цепи.
            // Без внутреннего накопителя активная разность входа/выхода — фактическое тепло;
            // минимум обмоток/балласта защищает от малой погрешности вычитания близких величин.
            lossWatts=Math.max(lossWatts,inputWatts-outputWatts);
        }
        double bleed = Double.isFinite(inputRms) ? inputRms*inputRms/BLEED_OHMS : 0;
        inputWatts += bleed; lossWatts += bleed;
        if (cooldown>0) cooldown--;
        overload = Math.clamp(overload + (Math.abs(inputWatts)>12 ? 1 : -2),0,40);
        if (!Double.isFinite(inputRms) || !Double.isFinite(outputRms) || !Double.isFinite(primaryAmps) || !Double.isFinite(secondaryAmps) || !Double.isFinite(inputWatts) || !Double.isFinite(lossWatts) || inputRms>MAX_INPUT+1e-6 || primaryAmps>.5 || secondaryAmps>.025 || overload>=40) {
            cooldown=100; overload=0;
        }
        double[] a = results.getVoltages(node(part.a())), b = results.getVoltages(node(part.b()));
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for (int n=0;n<Math.min(a.length,b.length);n++) { double v=a[n]-b[n]; min=Math.min(min,v); max=Math.max(max,v); }
        enabled = cooldown==0 && Double.isFinite(inputRms) && inputRms<=MAX_INPUT+1e-6 && min<-.1 && max>.1;
    }

    double losses() { return Double.isFinite(lossWatts) ? lossWatts : 0; }
    double output() { return Double.isFinite(outputRms) ? outputRms : Double.POSITIVE_INFINITY; }
    double input() { return Double.isFinite(inputRms) ? inputRms : Double.POSITIVE_INFINITY; }
    double current() { return Math.max(Double.isFinite(primaryAmps) ? primaryAmps : Double.POSITIVE_INFINITY,Double.isFinite(secondaryAmps) ? secondaryAmps : Double.POSITIVE_INFINITY); }

    String status() {
        String state=cooldown>0 ? "Защита повышающего трансформатора" : enabled ? "Трансформатор ×10 работает" : inputRms<.1 ? "Нет входного питания" : "Нужен переменный ток до 48 В";
        return state + String.format(java.util.Locale.ROOT," · Вход %.2f В~ · Выход %.2f В~",Double.isFinite(inputRms)?inputRms:0,Double.isFinite(outputRms)?outputRms:0);
    }

    void read(CompoundTag tag) {
        enabled=stamped=false;
        cooldown=Math.clamp(tag.getInt("Cooldown"),0,100);
        double saved=tag.getDouble("Overload"); overload=Double.isFinite(saved)?Math.clamp(saved,0,40):0;
    }

    void write(CompoundTag tag) {
        tag.putInt("Cooldown",cooldown); tag.putDouble("Overload",overload);
        tag.putBoolean("AcInput",enabled);
        tag.putDouble("InputRms",Double.isFinite(inputRms)?inputRms:0);
        tag.putDouble("OutputRms",Double.isFinite(outputRms)?outputRms:0);
        tag.putDouble("InputAmps",Double.isFinite(primaryAmps)?primaryAmps:0);
        tag.putDouble("OutputAmps",Double.isFinite(secondaryAmps)?secondaryAmps:0);
        tag.putDouble("InputWatts",Double.isFinite(inputWatts)?inputWatts:0);
        tag.putDouble("LossWatts",losses());
    }
}
