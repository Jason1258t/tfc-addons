package ee.extras.machine;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerItemHandler;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public final class ElectricDeployerBlockEntity extends DeployerBlockEntity implements MotorMachine {
    private final MachineDrive drive = new MachineDrive(this, 32);

    private DeployerItemHandler automation;

    public ElectricDeployerBlockEntity(BlockPos pos, BlockState state) {
        super(EeExtras.ELECTRIC_DEPLOYER_BE.get(), pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) level.invalidateCapabilities(worldPosition);
    }

    public IItemHandler automation() {
        if (player == null) return null;
        if (automation == null) automation = new DeployerItemHandler(this);
        return automation;
    }

    @Override
    public MachineDrive drive() {
        return drive;
    }

    @Override
    protected Block getStressConfigKey() {
        return AllBlocks.DEPLOYER.get();
    }

    @Override
    public float getSpeed() {
        return drive.speed();
    }

    @Override
    public boolean processing() {
        Enum<?> phase = state;
        return !phase.name().equals("WAITING");
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide && level.hasNeighborSignal(worldPosition)) drive.setRunning(false);
        super.tick();
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        drive.write(tag, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        drive.read(tag, clientPacket);
    }
}
