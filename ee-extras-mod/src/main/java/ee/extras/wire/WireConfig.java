package ee.extras.wire;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Настройки декоративного провода (ee_extras-server.toml, раздел surface_wire) */
public final class WireConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ITEMS_PER_METER;
    public static final ModConfigSpec.DoubleValue MAX_LENGTH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("surface_wire");
        ITEMS_PER_METER = b.comment("Сколько предметов провода уходит на метр (округление вверх)")
                .defineInRange("itemsPerMeter", 1.0, 0.1, 16.0);
        MAX_LENGTH = b.comment("Наибольшая длина одного куска провода между концами или узлами, м")
                .defineInRange("maxLength", 16.0, 1.0, 128.0);
        b.pop();
        SPEC = b.build();
    }

    public static double itemsPerMeter() {
        return SPEC.isLoaded() ? ITEMS_PER_METER.get() : 1.0;
    }

    public static double maxLength() {
        return SPEC.isLoaded() ? MAX_LENGTH.get() : 16.0;
    }

    /** Предметов на длину length, вверх */
    public static int items(double length) {
        return (int) Math.ceil(length * itemsPerMeter() - 1e-6);
    }

    private WireConfig() {
    }
}
