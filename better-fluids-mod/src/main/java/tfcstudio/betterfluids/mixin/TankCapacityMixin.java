package tfcstudio.betterfluids.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tfcstudio.betterfluids.IronTankEntity;

@Mixin(value = FluidTankBlockEntity.class, remap = false)
abstract class TankCapacityMixin {
    @Redirect(
        method = {"createInventory", "applyFluidTankSize", "read", "getTankSize"},
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;getCapacityMultiplier()I"),
        require = 5
    )
    private int betterFluids$capacityPerBlock() {
        return (Object) this instanceof IronTankEntity tank
            ? tank.sectionCapacity()
            : FluidTankBlockEntity.getCapacityMultiplier();
    }
}
