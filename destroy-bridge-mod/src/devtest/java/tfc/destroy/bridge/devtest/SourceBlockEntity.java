package tfc.destroy.bridge.devtest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import tfc.destroy.bridge.fluid.SaltWaterAdapter;

/** Конечный источник TFC для проверки настоящей передачи жидкости, а не прямого создания результата. */
public final class SourceBlockEntity extends BlockEntity {
    public final FluidTank tank = new FluidTank(8000, SaltWaterAdapter::isSaltWater) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public SourceBlockEntity(BlockPos pos, BlockState state) {
        super(DestroyTest.SOURCE_BE.get(), pos, state);
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
