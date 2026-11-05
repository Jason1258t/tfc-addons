package tfcstudio.betterfluids.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = FluidTankModel.class, remap = false)
public interface TankModelInvoker {
    @Invoker("<init>")
    static FluidTankModel create(BakedModel model, CTSpriteShiftEntry side,
                                 CTSpriteShiftEntry top, CTSpriteShiftEntry inner) {
        throw new AssertionError();
    }
}
