package ee.extras.mixin;

import com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ee.extras.lines.LinesClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Выбор гирлянды катушкой ведёт на её нижний электрический контакт. */
@Mixin(value = WireApplyingBehaviour.class, remap = false)
public abstract class InsulatorTipTargetMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;getHitNode(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/client/multiplayer/ClientLevel;)Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;"),
            require = 1)
    private static InWorldNode eeExtras$stringContact(BlockHitResult hit, ClientLevel level, Operation<InWorldNode> original) {
        return LinesClient.insulatorTip(hit, original.call(hit, level));
    }
}
