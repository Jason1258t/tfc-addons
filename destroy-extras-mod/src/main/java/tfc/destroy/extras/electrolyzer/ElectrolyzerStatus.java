package tfc.destroy.extras.electrolyzer;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum ElectrolyzerStatus {
    IDLE,
    WAITING_FOR_POWER,
    RUNNING,
    BLOCKED_OUTPUT,
    FAULT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component text() {
        return Component.translatable("status.destroy_extras.electrolyzer." + id());
    }
}
