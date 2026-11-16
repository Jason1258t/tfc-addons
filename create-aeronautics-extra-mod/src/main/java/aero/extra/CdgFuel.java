package aero.extra;

import com.jesz.createdieselgenerators.CDGRegistries;
import com.jesz.createdieselgenerators.fuel_type.FuelType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

final class CdgFuel {
    static boolean accepts(Level level, FluidStack stack) {
        return FuelType.getTypeFor(level.registryAccess().lookupOrThrow(CDGRegistries.FUEL_TYPE),
                stack.getFluid()) != FuelType.EMPTY;
    }
}
