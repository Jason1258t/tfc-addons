package ee.extras.pcb;

import java.util.Locale;

/** Две операции промышленного производства многослойных плат. */
public enum PcbMachineKind {
    PHOTOLITHOGRAPHY,
    METALLIZATION;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int inputSlots() {
        return this == PHOTOLITHOGRAPHY ? 3 : 2;
    }

    public static PcbMachineKind byId(String id) {
        for (PcbMachineKind kind : values()) if (kind.id().equals(id)) return kind;
        throw new IllegalArgumentException("Неизвестная машина плат: " + id);
    }
}
