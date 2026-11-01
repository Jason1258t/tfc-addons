package tfc.destroy.extras.etching;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum EtchingStatus {
    IDLE,
    RUNNING,
    BLOCKED_OUTPUT,
    FAULT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component text() {
        return Component.translatable("status.destroy_extras.etching." + id());
    }
}
