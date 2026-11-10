package ee.extras.mixin;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EE1.1.2: после перестройки сети сохранённый индекс провода может принадлежать другому узлу.
 * Чужое напряжение давало ложный ток/нагрев и сжигало исправный провод. Проверяем идентичность
 * узла; формулы сопротивления, тока, нагрева и штатное разрушение остаются у EE.
 */
@Mixin(value=SimulationResults.class,remap=false)
public abstract class WireNodeHintMixin {
    @Shadow @Final public CircuitBuilder circuitBuilder;

    @Inject(method="getNodeID(Lcom/george_vi/electroenergetics/foundation/nodes/Node;I)I",
            at=@At("HEAD"),cancellable=true,require=1)
    private void eeExtras$validateHint(Node node,int hint,CallbackInfoReturnable<Integer> cir) {
        // Авторитетен индекс из карты: старый список может содержать одноимённый узел.
        var actual=circuitBuilder.getNode(node);
        cir.setReturnValue(actual==null?-1:actual.ordinal);
    }
}
