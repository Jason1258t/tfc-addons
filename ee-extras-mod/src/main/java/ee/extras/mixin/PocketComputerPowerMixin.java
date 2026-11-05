package ee.extras.mixin;

import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.pocket.core.PocketBrain;
import dan200.computercraft.shared.pocket.core.PocketHolder;
import dan200.computercraft.shared.pocket.core.PocketServerComputer;
import ee.extras.computer.PocketCharge;
import ee.extras.computer.PocketPowerSession;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dan200.computercraft.shared.pocket.items.PocketComputerItem", remap = false)
public abstract class PocketComputerPowerMixin {
    @Redirect(method = "getOrCreateBrain", at = @At(value = "INVOKE",
            target = "Ldan200/computercraft/shared/pocket/core/PocketServerComputer;turnOn()V"), require = 1)
    private void eeExtras$restore(PocketServerComputer computer, PocketHolder holder, ItemStack stack) {
        ((PocketPowerSession) (Object) computer).eeExtras$refreshBattery(stack);
        computer.turnOn();
    }

    @Inject(method = "getOrCreateBrain", at = @At("RETURN"), require = 1)
    private void eeExtras$refresh(PocketHolder holder, ItemStack stack, CallbackInfoReturnable<PocketBrain> cir) {
        ((PocketPowerSession) (Object) cir.getReturnValue().computer()).eeExtras$refreshBattery(stack);
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void eeExtras$drain(ItemStack stack, PocketHolder holder, PocketBrain brain, CallbackInfo ci) {
        var computer = brain.computer();
        int charge = PocketCharge.get(stack);
        if (computer.isOn() && charge > 0) {
            int watts = computer.getFamily() == ComputerFamily.ADVANCED ? 6 : 3;
            PocketCharge.set(stack, charge - watts);
            holder.setChanged();
        }
        ((PocketPowerSession) (Object) computer).eeExtras$refreshBattery(stack);
        if (PocketCharge.get(stack) == 0 && computer.isOn()) computer.shutdown();
    }
}
