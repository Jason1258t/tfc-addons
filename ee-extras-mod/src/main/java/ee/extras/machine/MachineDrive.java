package ee.extras.machine;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.nbt.CompoundTag;

final class MachineDrive {
    private final KineticBlockEntity machine;
    private final int rpm;
    private float speed;

    MachineDrive(KineticBlockEntity machine, int rpm) {
        this.machine = machine;
        this.rpm = rpm;
    }

    float speed() {
        return speed;
    }

    float requiredStress() {
        return rpm * machine.calculateStressApplied();
    }

    void setRunning(boolean running) {
        float previous = speed;
        speed = running ? rpm : 0;
        if (speed != previous) {
            machine.onSpeedChanged(previous);
            machine.sendData();
        }
    }

    void write(CompoundTag tag, boolean clientPacket) {
        if (clientPacket) tag.putFloat("MotorSpeed", speed);
    }

    void read(CompoundTag tag, boolean clientPacket) {
        speed = clientPacket ? tag.getFloat("MotorSpeed") : 0;
    }
}
