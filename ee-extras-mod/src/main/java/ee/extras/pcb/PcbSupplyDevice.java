package ee.extras.pcb;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerBehaviour;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

final class PcbSupplyDevice extends BundledWireTerminationDevice {
    static final int INPUT_A = 1;
    static final int INPUT_B = 2;
    private static final double NOMINAL_INPUT = 300;
    private static final double MAX_POWER = 48;
    private static final double BUFFER = MAX_POWER * 4;
    private static final double OPEN = 1e9;
    private static final double START_RESISTANCE = NOMINAL_INPUT * NOMINAL_INPUT / MAX_POWER;
    private final InWorldNode[] transformerNodes;
    private final TransformerBehaviour.TransformerBehaviourDataHolder transformerData =
            new TransformerBehaviour.TransformerBehaviourDataHolder();
    private double stored;
    private double inputResistance = START_RESISTANCE;
    private double inputSquared;
    private double measuredOutput;
    private double outputMin;
    private double outputMax;

    PcbSupplyDevice(Level level, BlockPos pos, DevicesSavedData devices, SimulatedDeviceType<?> type) {
        super(level, pos, devices, type);
        transformerNodes = new InWorldNode[] {new InWorldNode(INPUT_A, pos), new InWorldNode(INPUT_B, pos),
                new InWorldNode(PcbSupplyBlock.OUTPUT_A, pos), new InWorldNode(PcbSupplyBlock.OUTPUT_B, pos),
                new InWorldNode(5, pos), new InWorldNode(6, pos)};
    }

    @Override
    public void preTick(BridgeCollector collector) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PcbSupplyBlock block)) return;
        double volts = PcbSupplyBlock.VOLTS[state.getValue(PcbSupplyBlock.SETTING)];
        if (!block.rectified) {
            TransformerBehaviour.preTick(transformerNodes, NOMINAL_INPUT / volts, pos, collector, transformerData);
            return;
        }
        var builder = collector.builder(pos);
        builder.resistor(INPUT_A, INPUT_B, inputResistance);
        // Конденсатор заряжается от входной сети. Выход расходует только накопленную энергию.
        if (stored > 0.01 && inputSquared >= 100) {
            double output = volts * Math.min(1, Math.sqrt(inputSquared) / 150);
            builder.energyLimitedSource(PcbSupplyBlock.OUTPUT_B, PcbSupplyBlock.OUTPUT_A,
                    Math.min(stored, MAX_POWER), output);
        } else {
            builder.resistor(PcbSupplyBlock.OUTPUT_A, PcbSupplyBlock.OUTPUT_B, OPEN);
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PcbSupplyBlock block)) return;
        measuredOutput = Math.sqrt(results.getVoltageAtSqr(pos, PcbSupplyBlock.OUTPUT_A, PcbSupplyBlock.OUTPUT_B));
        double[] a = results.getVoltages(transformerNodes[2]);
        double[] b = results.getVoltages(transformerNodes[3]);
        outputMin = Double.POSITIVE_INFINITY;
        outputMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            outputMin = Math.min(outputMin, a[i] - b[i]);
            outputMax = Math.max(outputMax, a[i] - b[i]);
        }
        if (!block.rectified) return;
        double sampled = results.getVoltageAtSqr(pos, INPUT_A, INPUT_B);
        if (!Double.isFinite(sampled)) return;
        inputSquared += (sampled - inputSquared) * 0.2;
        double used = measuredOutput * Math.abs(results.getCurrentThrough(pos, PcbSupplyBlock.OUTPUT_A, PcbSupplyBlock.OUTPUT_B));
        if (!Double.isFinite(used)) used = 0;
        stored = Math.clamp(stored + sampled / inputResistance - used, 0, BUFFER);
        double demand = Math.clamp((BUFFER - stored) / 4, 0.01, MAX_POWER);
        inputResistance = Math.clamp(Math.max(inputSquared, 100) / demand, START_RESISTANCE / 4, OPEN);
        deviceSD.setDirty();
    }

    @Override
    public boolean shouldRemove(net.minecraft.world.level.block.state.BlockState before,
                                net.minecraft.world.level.block.state.BlockState after) {
        return before.getBlock() != after.getBlock();
    }

    @Override
    public void read(CompoundTag tag) {
        stored = Math.clamp(tag.getDouble("Stored"), 0, BUFFER);
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Stored", stored);
        tag.putDouble("Output", measuredOutput);
        tag.putDouble("OutputMin", Double.isFinite(outputMin) ? outputMin : 0);
        tag.putDouble("OutputMax", Double.isFinite(outputMax) ? outputMax : 0);
    }
}
