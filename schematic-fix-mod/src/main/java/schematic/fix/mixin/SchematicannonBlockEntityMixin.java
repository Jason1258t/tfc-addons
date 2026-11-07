package schematic.fix.mixin;

import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import schematic.fix.SchematicFix;

/**
 * Решение пушки «ставить ли блок сюда» (shouldPlace). Им же считается список материалов.
 * Create сверяет состояние блока строго: если стоящий блок того же вида отличается хоть одним свойством
 * (мостки Create Deco сами пересчитывают «пол», копикаты, повёрнутые ступеньки), пушка ставит его заново —
 * списывает предмет, а стоящий блок уничтожает без дропа. Так повторный запуск схемы «съедает» блоки.
 * Fast Schematic Cannon отказывается ломать блоки из своего списка уже после списания предметов и пороха.
 */
@Mixin(value = SchematicannonBlockEntity.class, remap = false)
public abstract class SchematicannonBlockEntityMixin {
    @Inject(method = "shouldPlace", at = @At("HEAD"), cancellable = true)
    private void schematicFix$skipSameAndForbidden(BlockPos pos, BlockState state, BlockEntity be, BlockState toReplace,
            BlockState toReplaceOther, boolean isNormalCube, CallbackInfoReturnable<Boolean> cir) {
        if (!state.isAir() && toReplace.is(state.getBlock())) {
            cir.setReturnValue(false);
            return;
        }
        if (!toReplace.isAir() && SchematicFix.isForbiddenByFastCannon(toReplace.getBlock())) {
            cir.setReturnValue(false);
        }
    }
}
