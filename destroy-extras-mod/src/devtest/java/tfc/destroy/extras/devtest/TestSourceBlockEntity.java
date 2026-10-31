package tfc.destroy.extras.devtest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class TestSourceBlockEntity extends BlockEntity {
    public final FluidTank tank = new FluidTank(8000, stack -> true) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    public int savedProgress;
    public int savedBatches;
    public int savedMembraneDamage;
    public FluidStack savedMixture = FluidStack.EMPTY;

    public TestSourceBlockEntity(BlockPos pos, BlockState state) {
        super(DestroyExtrasTest.SOURCE_BE.get(), pos, state);
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        IFluidHandler destination = serverLevel.getCapability(Capabilities.FluidHandler.BLOCK,
            worldPosition.east(), Direction.WEST);
        if (destination != null) FluidUtil.tryFluidTransfer(destination, tank, 1000, true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("SavedProgress", savedProgress);
        tag.putInt("SavedBatches", savedBatches);
        tag.putInt("SavedMembraneDamage", savedMembraneDamage);
        if (!savedMixture.isEmpty()) tag.put("SavedMixture", savedMixture.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        savedProgress = tag.getInt("SavedProgress");
        savedBatches = tag.getInt("SavedBatches");
        savedMembraneDamage = tag.getInt("SavedMembraneDamage");
        savedMixture = FluidStack.parseOptional(registries, tag.getCompound("SavedMixture"));
    }
}
