package ee.extras.machine;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.nbt.CompoundTag;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class MachineDrive {
    private final KineticBlockEntity machine;
    private final int defaultRpm;
    private ScrollValueBehaviour setting;
    final MachineControls controls;
    private float speed;

    MachineDrive(KineticBlockEntity machine, int rpm) {
        this.machine = machine;
        this.defaultRpm = rpm;
        controls = new MachineControls(machine);
    }

    public static void registerControls() { MachineControls.register(); }

    static void addBehaviours(KineticBlockEntity machine, List<BlockEntityBehaviour> behaviours) {
        var setting = new ScrollValueBehaviour(
                Component.translatable("ee_extras.machine.speed"), machine, new SpeedSlot())
                .between(1, 256);
        setting.value = 32;
        behaviours.add(setting);
    }

    private int rpm() {
        if (setting == null) setting = machine.getBehaviour(ScrollValueBehaviour.TYPE);
        return setting == null ? defaultRpm : setting.getValue();
    }

    boolean enabled() {
        return !machine.getLevel().hasNeighborSignal(machine.getBlockPos()) && controls.enabled();
    }

    private static final class SpeedSlot extends ValueBoxTransform.Sided {
        SpeedSlot() { scale = 0.2f; }
        @Override protected Vec3 getSouthLocation() {
            return new Vec3(0.5, 0.8, 1.01);
        }
        @Override protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal();
        }
    }

    float speed() {
        return speed;
    }

    float requiredStress() {
        return rpm() * machine.calculateStressApplied();
    }

    void setRunning(boolean running) {
        float previous = speed;
        speed = running && enabled() ? rpm() : 0;
        if (speed != previous) {
            machine.onSpeedChanged(previous);
            machine.sendData();
        }
    }

    void write(CompoundTag tag, boolean clientPacket) {
        controls.write(tag, clientPacket);
        if (clientPacket) tag.putFloat("MotorSpeed", speed);
    }

    void read(CompoundTag tag, boolean clientPacket) {
        controls.read(tag, clientPacket);
        speed = clientPacket ? tag.getFloat("MotorSpeed") : 0;
    }
}
