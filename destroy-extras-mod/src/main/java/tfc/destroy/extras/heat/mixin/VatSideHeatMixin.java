package tfc.destroy.extras.heat.mixin;

import blusunrize.immersiveengineering.common.blocks.metal.FurnaceHeaterBlockEntity;
import com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import petrolpark.mc.destroy.core.chemistry.vat.IVatHeaterBlock;
import petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity;
import tfc.destroy.extras.heat.VatElectricHeat;


@Mixin(value = VatSideBlockEntity.class, remap = false)
abstract class VatSideHeatMixin implements VatElectricHeat {
    @Shadow public Direction direction;
    @Shadow protected float oldPower;

    @Unique private long destroyExtras$lastIeHeat = Long.MIN_VALUE;

    @Override
    public void destroyExtras$acceptIeHeat(long gameTime) {
        destroyExtras$lastIeHeat = gameTime;
    }

    @Redirect(method = "setPowerFromAdjacentBlock", at = @At(value = "INVOKE", target =
        "Lpetrolpark/mc/destroy/core/chemistry/vat/IVatHeaterBlock;getHeatingPower(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)F"))
    private float destroyExtras$readHeatingPower(Level level, BlockPos pos, Direction face) {
        var wall = (VatSideBlockEntity) (Object) this;
        var controller = wall.getController();
        if (controller == null || !level.hasChunkAt(pos)) return 0f;
        var heater = level.getBlockEntity(pos);
        if (heater instanceof ResistiveHeaterBlockEntity ee) return VatElectricHeat.eePower(ee, face);
        if (heater instanceof FurnaceHeaterBlockEntity) {
            long now = level.getGameTime();
            // Порядок тиков двух блоков может отличаться. При остановке IE вклад исчезает после одного пропущенного тика.
            return destroyExtras$lastIeHeat >= now - 1 ? VatElectricHeat.maximumPower() : 0f;
        }
        float analog = VatElectricHeat.analogPower(heater, face, controller.getTemperature());
        return analog > 0f ? analog : IVatHeaterBlock.getHeatingPower(level, pos, face);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void destroyExtras$updateHeatingPower(CallbackInfo ci) {
        var wall = (VatSideBlockEntity) (Object) this;
        var level = wall.getLevel();
        if (level == null || level.isClientSide() || direction == null) return;
        var controller = wall.getController();
        if (controller == null) return;
        BlockPos source = wall.getBlockPos().relative(direction);
        // Destroy хранит старый вклад стенки вместе с суммой контроллера. Первый тик сверяет их с источником.
        float power = destroyExtras$readHeatingPower(level, source, direction.getOpposite());
        if (power != oldPower) {
            controller.changeHeatingPower(power - oldPower);
            oldPower = power;
            wall.setChanged();
            controller.setChanged();
        }
    }
}
