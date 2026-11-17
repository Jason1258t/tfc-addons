package ee.extras.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import ee.extras.motor.BeltMotorHost;
import ee.extras.motor.DriveTerminalsRenderer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class ClientDriveMountsMixin {
    @Inject(method = "read", at = @At("TAIL"), require = 1)
    private void eeExtras$trackDrive(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                     CallbackInfo ci) {
        if (clientPacket) {
            var entity = (KineticBlockEntity) (Object) this;
            if (entity.getLevel() != null && entity.getLevel().isClientSide)
                DriveTerminalsRenderer.track(entity, tag.contains(BeltMotorHost.MOUNT_SIDE_TAG));
        }
    }
}
