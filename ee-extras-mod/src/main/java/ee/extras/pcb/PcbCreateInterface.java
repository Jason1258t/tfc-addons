package ee.extras.pcb;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Пассивные входы и питание аппаратного интерфейса информационных устройств Create. */
final class PcbCreateInterface {
    private static final double SUPPLY_OHMS = 10000;
    private static final double INPUT_OHMS = 1e9;
    private final BlockPos pos;
    private final int base;
    private boolean powered;
    private boolean high;
    private double supply;
    private double analog;
    private double digital;
    private double watts;

    PcbCreateInterface(BlockPos pos, int index) {
        this.pos = pos;
        base = 18000 + index * 3;
    }

    void preTick(BridgeCollector collector, PcbCircuitDevice.Part part) {
        var builder = collector.builder(pos);
        // Каждый вход имеет собственный узел, чтобы сохранить внешние параллельные ветви EE.
        builder.node(base).resistor(part.a(), base, SUPPLY_OHMS / 2)
                .resistor(base, part.b(), SUPPLY_OHMS / 2);
        builder.node(base + 1).resistor(part.coilA(), base + 1, INPUT_OHMS / 2)
                .resistor(base + 1, part.b(), INPUT_OHMS / 2);
        builder.node(base + 2).resistor(part.coilB(), base + 2, INPUT_OHMS / 2)
                .resistor(base + 2, part.b(), INPUT_OHMS / 2);
    }

    PcbBridgeRectifier.Measurement measure(SimulationResults results, PcbCircuitDevice.Part part) {
        double[] positive = voltages(results, part.a());
        double[] ground = voltages(results, part.b());
        double[] analogInput = voltages(results, part.coilA());
        double[] digitalInput = voltages(results, part.coilB());
        powered = positive.length > 0;
        supply = analog = digital = watts = 0;
        double maxVoltage = 0;
        double maxCurrent = 0;
        for (int sample = 0; sample < positive.length; sample++) {
            double powerVoltage = positive[sample] - ground[sample];
            double analogVoltage = analogInput[sample] - ground[sample];
            double digitalVoltage = digitalInput[sample] - ground[sample];
            powered &= Double.isFinite(powerVoltage) && powerVoltage >= 3 && powerVoltage <= 6
                    && Double.isFinite(analogVoltage) && Double.isFinite(digitalVoltage);
            supply += powerVoltage;
            analog += analogVoltage;
            digital += digitalVoltage;
            watts += powerVoltage * powerVoltage / SUPPLY_OHMS
                    + analogVoltage * analogVoltage / INPUT_OHMS
                    + digitalVoltage * digitalVoltage / INPUT_OHMS;
            maxVoltage = Math.max(maxVoltage, Math.max(Math.abs(powerVoltage),
                    Math.max(Math.abs(analogVoltage), Math.abs(digitalVoltage))));
            maxCurrent = Math.max(maxCurrent, Math.max(Math.abs(powerVoltage) / SUPPLY_OHMS,
                    Math.max(Math.abs(analogVoltage), Math.abs(digitalVoltage)) / INPUT_OHMS));
        }
        if (positive.length > 0) {
            supply /= positive.length;
            analog /= positive.length;
            digital /= positive.length;
            watts /= positive.length;
        }
        if (!powered) high = false;
        else if (digital >= supply * .6) high = true;
        else if (digital <= supply * .4) high = false;
        return new PcbBridgeRectifier.Measurement(watts, maxVoltage, maxCurrent);
    }

    private double[] voltages(SimulationResults results, int node) {
        return results.getVoltages(new InWorldNode(node, pos));
    }

    boolean powered() { return powered; }
    boolean high() { return powered && high; }
    double analog() { return analog; }

    void read(CompoundTag tag) {
        high = tag.getBoolean("High");
        powered = false;
    }

    void write(CompoundTag tag) {
        tag.putBoolean("Powered", powered);
        tag.putBoolean("High", high);
        tag.putDouble("Supply", supply);
        tag.putDouble("Analog", analog);
        tag.putDouble("Digital", digital);
        tag.putDouble("Watts", watts);
    }

    String status() {
        return String.format(java.util.Locale.ROOT, "Create · %.3f В · А %.3f В · Л %s · %s",
                supply, analog, high ? "1" : "0", powered ? "работает" : "нет питания");
    }
}
