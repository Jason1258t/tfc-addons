package ee.extras.machine;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ElectricPressBlockEntity extends MechanicalPressBlockEntity {
    static final int RPM = 32;
    private float motorSpeed;

    public ElectricPressBlockEntity(BlockPos pos, BlockState state) {
        super(EeExtras.ELECTRIC_PRESS_BE.get(), pos, state);
    }

    @Override
    protected Block getStressConfigKey() {
        return AllBlocks.MECHANICAL_PRESS.get();
    }

    @Override
    public float getSpeed() {
        return motorSpeed;
    }

    float requiredStress() {
        return RPM * calculateStressApplied();
    }

    boolean processing() {
        return isRunning();
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide && level.hasNeighborSignal(worldPosition)) {
            setMotorRunning(false);
        }
        super.tick();
    }

    void setMotorRunning(boolean running) {
        float previous = motorSpeed;
        motorSpeed = running ? RPM : 0;
        if (motorSpeed != previous) {
            onSpeedChanged(previous);
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) tag.putFloat("MotorSpeed", motorSpeed);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        motorSpeed = clientPacket ? tag.getFloat("MotorSpeed") : 0;
    }
}
