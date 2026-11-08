package ee.extras.pcb;

import com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties;

/** Метка штатного диода EE для ограниченной итерации матрицы сети, содержащей мост платы. */
public final class PcbBridgeDiode extends DiodeProperties {
    public record Entry(long nodes,DiodeProperties diode) {}
    public boolean converged;
    public int iterations;
    public double residual;
}
