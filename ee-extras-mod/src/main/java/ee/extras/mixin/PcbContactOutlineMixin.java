package ee.extras.mixin;

import com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour;
import ee.extras.pcb.PcbContactOutline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** EE 1.1.2: корректируем уже созданные рамки, сохраняя цвета доступности соединения. */
@Mixin(value = WireApplyingBehaviour.class, remap = false)
public abstract class PcbContactOutlineMixin {
    @Inject(method = "tick", at = @At("RETURN"), require = 1)
    private static void eeExtras$contactSize(CallbackInfo callback) {
        PcbContactOutline.update();
    }
}
