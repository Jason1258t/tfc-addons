package ee.extras.mixin;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import ee.extras.cable.CableCapRender;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Тот же ввод крышки в обычном рендерере EE, когда Flywheel отключён. */
@Mixin(value = WireRenderer.class, remap = false)
public abstract class WireRendererCapMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;getPosition(Lnet/minecraft/world/level/Level;F)Lnet/minecraft/world/phys/Vec3;"),
            require = 2)
    private static Vec3 eeExtras$capInlet(InWorldNode node, Level level, float tick, Operation<Vec3> original,
                                         @Local WireData wire) {
        Vec3 inlet = CableCapRender.cableInlet(level, node, wire.wireType());
        return inlet == null ? original.call(node, level, tick) : inlet;
    }
}
