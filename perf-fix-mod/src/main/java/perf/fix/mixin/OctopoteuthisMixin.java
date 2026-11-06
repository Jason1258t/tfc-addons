package perf.fix.mixin;

import net.dries007.tfc.common.entities.aquatic.Octopoteuthis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import perf.fix.GlowLight;

/**
 * TFC 4.2.9: при исчезновении октопотеутиса IGlow.tryRemoveLight проверяет, загружен ли чанк самого существа, а блок
 * света читает по запомненной позиции — она бывает (0,0,0) или далеко, и сервер синхронно грузит тот чанк.
 */
@Pseudo
@Mixin(value = Octopoteuthis.class, remap = false)
public abstract class OctopoteuthisMixin {
    @Redirect(method = "remove", at = @At(value = "INVOKE",
            target = "Lnet/dries007/tfc/common/entities/aquatic/Octopoteuthis;tryRemoveLight()V"), remap = false)
    private void perfFix$removeLightIfLoaded(Octopoteuthis self) {
        if (GlowLight.ENABLED) {
            GlowLight.removeIfLoaded(self);
        } else {
            self.tryRemoveLight();
        }
    }
}
