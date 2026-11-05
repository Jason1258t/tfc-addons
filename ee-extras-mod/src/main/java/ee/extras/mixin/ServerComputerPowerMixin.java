package ee.extras.mixin;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.pocket.core.PocketServerComputer;
import ee.extras.computer.ComputerPowerBlock;
import ee.extras.computer.ComputerPowerDevice;
import ee.extras.computer.PocketCharge;
import ee.extras.computer.PocketPowerSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dan200.computercraft.shared.computer.core.ServerComputer", remap = false)
public abstract class ServerComputerPowerMixin implements PocketPowerSession {
    @Shadow public abstract ServerLevel getLevel();
    @Shadow public abstract BlockPos getPosition();
    @Shadow public abstract ComputerFamily getFamily();
    @Shadow public abstract boolean isOn();
    @Shadow public abstract void shutdown();
    @Shadow public abstract void turnOn();

    @Unique private int eeExtras$startTicks;
    @Unique private boolean eeExtras$bootQueued;
    @Unique private boolean eeExtras$charged;
    @Unique private long eeExtras$batteryTick = Long.MIN_VALUE;

    @Override
    public void eeExtras$refreshBattery(ItemStack stack) {
        eeExtras$charged = PocketCharge.get(stack) > 0;
        eeExtras$batteryTick = getLevel().getGameTime();
    }

    @Inject(method = "turnOn", at = @At("HEAD"), cancellable = true, require = 1)
    private void eeExtras$start(CallbackInfo ci) {
        if (eeExtras$isPocket()) {
            if (!eeExtras$hasBattery()) ci.cancel();
        } else if (eeExtras$isDesktop()) {
            ComputerPowerDevice inlet = eeExtras$inlet();
            if (inlet == null) {
                ci.cancel();
                return;
            }
            inlet.requestLoad(eeExtras$watts());
            if (!inlet.hasPower()) {
                if (eeExtras$startTicks == 0) eeExtras$startTicks = 40;
                ci.cancel();
            } else if (!isOn()) {
                // CC запускает Lua асинхронно. Не снимать нагрузку, пока запуск не подтвердится.
                eeExtras$bootQueued = true;
                eeExtras$startTicks = Math.max(eeExtras$startTicks, 20);
            }
        }
    }

    @Inject(method = "reboot", at = @At("HEAD"), cancellable = true, require = 1)
    private void eeExtras$reboot(CallbackInfo ci) {
        if (eeExtras$isPocket()) {
            if (!eeExtras$hasBattery()) ci.cancel();
        } else if (eeExtras$isDesktop()) {
            ComputerPowerDevice inlet = eeExtras$inlet();
            if (inlet == null || !inlet.hasPower()) ci.cancel();
        }
    }

    @Inject(method = "tickServer", at = @At("HEAD"), require = 1)
    private void eeExtras$powerTick(CallbackInfo ci) {
        if (eeExtras$isPocket()) {
            if (!eeExtras$hasBattery()) shutdown();
            return;
        }
        if (!eeExtras$isDesktop() || (!isOn() && eeExtras$startTicks == 0)) return;
        ComputerPowerDevice inlet = eeExtras$inlet();
        if (inlet != null) inlet.requestLoad(eeExtras$watts());

        if (isOn()) {
            eeExtras$startTicks = 0;
            eeExtras$bootQueued = false;
            if (inlet == null || !inlet.hasPower()) shutdown();
        } else if (inlet == null) {
            eeExtras$startTicks = 0;
            eeExtras$bootQueued = false;
        } else if (!eeExtras$bootQueued && inlet.hasPower()) {
            turnOn();
        } else if (--eeExtras$startTicks == 0) {
            eeExtras$bootQueued = false;
        }
    }

    @Unique
    private boolean eeExtras$isDesktop() {
        return getFamily() != ComputerFamily.COMMAND && getLevel().isLoaded(getPosition())
                && getLevel().getBlockEntity(getPosition()) instanceof ComputerBlockEntity block
                && block.getServerComputer() == (Object) this;
    }

    @Unique
    private boolean eeExtras$isPocket() {
        return (Object) this instanceof PocketServerComputer;
    }

    @Unique
    private boolean eeExtras$hasBattery() {
        return eeExtras$charged && eeExtras$batteryTick >= getLevel().getGameTime() - 2;
    }

    @Unique
    private int eeExtras$watts() {
        return getFamily() == ComputerFamily.ADVANCED ? 180 : 80;
    }

    @Unique
    private ComputerPowerDevice eeExtras$inlet() {
        ServerLevel level = getLevel();
        BlockPos position = getPosition();
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = position.relative(direction);
            if (!level.isLoaded(adjacent)) continue;
            var state = level.getBlockState(adjacent);
            if (state.getBlock() instanceof ComputerPowerBlock block && !block.isCharger()
                    && state.getValue(ComputerPowerBlock.FACING) == direction) {
                return DevicesSavedData.load(level).getDevice(adjacent, ComputerPowerDevice.class);
            }
        }
        return null;
    }
}
