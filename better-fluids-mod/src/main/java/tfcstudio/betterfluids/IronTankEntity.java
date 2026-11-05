package tfcstudio.betterfluids;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class IronTankEntity extends FluidTankBlockEntity {
    public static final int CAPACITY = 14_000;

    IronTankEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    IFluidHandler fluidHandler() {
        FluidTankBlockEntity controller = getControllerBE();
        return controller instanceof IronTankEntity ? controller.getTankInventory() : null;
    }

    @Override
    public void updateBoilerState() {
        // Железный резервуар хранит жидкость и не превращается в паровой котёл.
    }
}
