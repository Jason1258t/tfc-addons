package ee.extras.machine;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ElectricPressBlockEntity extends MechanicalPressBlockEntity implements MotorMachine {
    static final int RPM = 32;
    private final MachineDrive drive = new MachineDrive(this, RPM);

    @Override public MachineDrive drive() { return drive; }

    @Override public void addBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        MachineDrive.addBehaviours(this, behaviours);
    }

    public ElectricPressBlockEntity(BlockPos pos, BlockState state) {
        super(EeExtras.ELECTRIC_PRESS_BE.get(), pos, state);
    }

    @Override
    protected Block getStressConfigKey() {
        return AllBlocks.MECHANICAL_PRESS.get();
    }

    @Override
    public float getSpeed() {
        return drive.speed();
    }

    float requiredStress() {
        return drive.requiredStress();
    }

    public boolean processing() {
        return isRunning();
    }

    @Override public void destroy() {
        drive.controls.drop();
        super.destroy();
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide && !drive.enabled()) {
            setMotorRunning(false);
        }
        super.tick();
        drive.controls.tick();
    }

    void setMotorRunning(boolean running) {
        drive.setRunning(running);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        drive.write(tag, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        drive.read(tag, clientPacket);
    }
}
