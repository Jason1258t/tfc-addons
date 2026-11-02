package tfc.destroy.extras.devtest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Накапливает продукты 8/32 установок, не удаляя их и не создавая обходного бесконечного стока. */
public final class TestSinkBlockEntity extends BlockEntity {
    public final FluidTank tank = new FluidTank(1_000_000, stack -> true) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    TestSinkBlockEntity(BlockPos pos, BlockState state) {
        super(M5LoadTest.SINK_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
