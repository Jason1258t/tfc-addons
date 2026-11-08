package ee.extras.pcb;

import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Понижающий трансформатор L1: настоящая связанная цепь EE, без синтетического источника энергии. */
final class PcbOnboardTransformer {
    private static final int PRIMARY_SENSE = PcbPowerInlet.BASE + 16, SECONDARY_SENSE = PcbPowerInlet.BASE + 17, OUTPUT_LIMIT = PcbPowerInlet.BASE + 18;
    private static final double WINDING_OHMS = .1, MAX_WATTS = 12, MAX_INPUT = 360, FUSE_AMPS = .08;
    private final BlockPos pos;
    private final TransformerBehaviour.TransformerBehaviourDataHolder data = new TransformerBehaviour.TransformerBehaviourDataHolder();
    private boolean enabled, stamped;
    private int cooldown;
    private double overload, inputRms, outputRms, inputWatts, lossWatts, outputAmps;

    PcbOnboardTransformer(BlockPos pos) { this.pos = pos; }

    void reset() {
        enabled = stamped = false;
        cooldown = 0;
        overload = inputRms = outputRms = inputWatts = lossWatts = outputAmps = 0;
    }

    void preTick(BridgeCollector collector, PcbCircuitDevice.Part part) {
        var builder = collector.builder(pos);
        builder.node(PcbPowerInlet.BASE).resistor(PcbPowerInlet.INPUT_A, PcbPowerInlet.INPUT_B, 1e8);
        stamped = enabled && cooldown == 0;
        if (stamped) {
            TransformerBehaviour.preTick(nodes(part), 300 / part.threshold(), pos, collector, data);
            builder.resistor(OUTPUT_LIMIT, part.b(), limitResistance(part));
        } else builder.resistor(part.a(), part.b(), 1e9);
    }

    // При номинальном входе КЗ ограничено и током дорожек L1, и тепловой мощностью корпуса.
    private static double limitResistance(PcbCircuitDevice.Part part) {
        return Math.max(part.threshold() / .5, part.threshold()*part.threshold() / MAX_WATTS);
    }

    private InWorldNode[] nodes(PcbCircuitDevice.Part part) {
        return new InWorldNode[]{new InWorldNode(PcbPowerInlet.INPUT_A,pos), new InWorldNode(PcbPowerInlet.INPUT_B,pos),
                new InWorldNode(part.a(),pos), new InWorldNode(OUTPUT_LIMIT,pos),
                new InWorldNode(PRIMARY_SENSE,pos), new InWorldNode(SECONDARY_SENSE,pos)};
    }

    void postTick(SimulationResults results, PcbCircuitDevice.Part part) {
        inputRms = Math.sqrt(results.getVoltageAtSqr(pos, PcbPowerInlet.INPUT_A, PcbPowerInlet.INPUT_B));
        outputRms = Math.sqrt(results.getVoltageAtSqr(pos, part.a(), part.b()));
        inputWatts = lossWatts = outputAmps = 0;
        double primaryAmps = 0;
        if (stamped) {
            primaryAmps = Math.sqrt(results.getVoltageAtSqr(pos, PcbPowerInlet.INPUT_A, PRIMARY_SENSE)) / WINDING_OHMS;
            outputAmps = Math.sqrt(results.getVoltageAtSqr(pos, SECONDARY_SENSE, OUTPUT_LIMIT)) / WINDING_OHMS;
            lossWatts = WINDING_OHMS * primaryAmps*primaryAmps + (WINDING_OHMS + limitResistance(part)) * outputAmps*outputAmps;
            double[] first = results.getVoltages(new InWorldNode(PcbPowerInlet.INPUT_A,pos));
            double[] second = results.getVoltages(new InWorldNode(PcbPowerInlet.INPUT_B,pos));
            double[] sense = results.getVoltages(new InWorldNode(PRIMARY_SENSE,pos));
            int count = Math.min(first.length, Math.min(second.length, sense.length));
            for (int n = 0; n < count; n++) inputWatts += (first[n]-second[n]) * (first[n]-sense[n]) / WINDING_OHMS;
            if (count != 0) inputWatts /= count;
            double[] outputA=results.getVoltages(new InWorldNode(part.a(),pos)),outputB=results.getVoltages(new InWorldNode(part.b(),pos));
            double[] returnEnd=results.getVoltages(new InWorldNode(OUTPUT_LIMIT,pos)),returnSense=results.getVoltages(new InWorldNode(SECONDARY_SENSE,pos));
            int outputs=Math.min(Math.min(outputA.length,outputB.length),Math.min(returnEnd.length,returnSense.length));
            double outputWatts=0;
            for(int n=0;n<outputs;n++)outputWatts+=(outputA[n]-outputB[n])*(returnEnd[n]-returnSense[n])/WINDING_OHMS;
            if(outputs>0)outputWatts/=outputs;
            // В реактивной цепи EE сохраняет регуляризующий резистор, который может убрать для чисто активной нагрузки.
            // Считаем фактическое тепло по активной разности портов, не теряя потери обмоток и балласта.
            lossWatts=Math.max(lossWatts,inputWatts-outputWatts);
        }
        // Резистор контроля входа также берёт энергию из сети и греет корпус.
        double sensingLoss = Double.isFinite(inputRms) ? inputRms*inputRms/1e8 : 0;
        inputWatts += sensingLoss;
        lossWatts += sensingLoss;
        if (cooldown > 0) cooldown--;
        overload = Math.clamp(overload + (Math.abs(inputWatts) > MAX_WATTS ? 1 : -2), 0, 40);
        if (!Double.isFinite(inputRms) || inputRms > MAX_INPUT || primaryAmps > FUSE_AMPS || overload >= 40) {
            cooldown = 100;
            overload = 0;
        }
        // TransformerBehaviour сам пропускает DC; допускаем только измеренную смену полярности входа.
        double[] first = results.getVoltages(new InWorldNode(PcbPowerInlet.INPUT_A,pos));
        double[] second = results.getVoltages(new InWorldNode(PcbPowerInlet.INPUT_B,pos));
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int n = 0; n < Math.min(first.length,second.length); n++) {
            double value = first[n]-second[n]; min = Math.min(min,value); max = Math.max(max,value);
        }
        enabled = cooldown == 0 && Double.isFinite(inputRms) && inputRms >= 10 && inputRms <= MAX_INPUT && min < -1 && max > 1;
    }

    double losses() { return Double.isFinite(lossWatts) ? lossWatts : 0; }
    double output() { return Double.isFinite(outputRms) ? outputRms : 0; }
    double current() { return Double.isFinite(outputAmps) ? outputAmps : 0; }

    String status(double nominal) {
        String state = cooldown > 0 ? "Защита: повтор через " + String.format(java.util.Locale.ROOT,"%.1f",cooldown/20.0) + " с"
                : enabled ? "Трансформатор работает" : inputRms < 10 ? "Нет входного питания" : "Нужен переменный ток";
        return state + String.format(java.util.Locale.ROOT," · Вход %.1f В · Выход %.2f В~ (холостой %.1f В~)",Double.isFinite(inputRms)?inputRms:0,output(),nominal);
    }

    void read(CompoundTag tag) {
        reset();
        cooldown = Math.clamp(tag.getInt("Cooldown"), 0, 100);
        overload = Math.clamp(tag.getDouble("Overload"), 0, 40);
    }

    void write(CompoundTag tag) {
        tag.putInt("Cooldown",cooldown);
        tag.putDouble("Overload",overload);
        tag.putDouble("InputRms",Double.isFinite(inputRms) ? inputRms : 0);
        tag.putDouble("OutputRms",output());
        tag.putDouble("InputWatts",Double.isFinite(inputWatts) ? inputWatts : 0);
        tag.putDouble("LossWatts",losses());
        tag.putDouble("OutputAmps",current());
        tag.putBoolean("AcInput",enabled);
    }
}
