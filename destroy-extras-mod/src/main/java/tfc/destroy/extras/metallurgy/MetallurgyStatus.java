package tfc.destroy.extras.metallurgy;

import net.minecraft.network.chat.Component;

public enum MetallurgyStatus {
    IDLE("idle"), WAITING_FOR_POWER("waiting_for_power"), RUNNING("running"), BLOCKED_OUTPUT("blocked_output"), FAULT("fault");
    private final String id;
    MetallurgyStatus(String id) { this.id = id; }
    public String id() { return id; }
    public Component text() { return Component.translatable("status.destroy_extras.metallurgy." + id); }
}
