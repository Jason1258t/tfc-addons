package ee.extras.machine;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ElectricMixerBlockEntity extends MechanicalMixerBlockEntity implements MotorMachine {
    private final MachineDrive drive = new MachineDrive(this, 32);

    public ElectricMixerBlockEntity(BlockPos pos, BlockState state) {
        super(EeExtras.ELECTRIC_MIXER_BE.get(), pos, state);
    }

    @Override
    public MachineDrive drive() {
        return drive;
    }

    @Override
    protected Block getStressConfigKey() {
        return AllBlocks.MECHANICAL_MIXER.get();
    }

    @Override
    public float getSpeed() {
        return drive.speed();
    }

    @Override
    public boolean processing() {
        return running;
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide && level.hasNeighborSignal(worldPosition)) drive.setRunning(false);
        super.tick();
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
