package ee.extras.pcb;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Состояние, которое машина показывает через очки инженера. */
public enum PcbMachineStatus {
    IDLE,
    RUNNING,
    NO_POWER,
    BLOCKED_OUTPUT,
    INVALID_DESIGN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent text() {
        return Component.translatable("goggles.ee_extras.pcb_machine.status." + id());
    }
}
