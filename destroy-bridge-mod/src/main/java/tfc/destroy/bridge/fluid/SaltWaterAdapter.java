package tfc.destroy.bridge.fluid;

import net.dries007.tfc.common.fluids.TFCFluids;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;

/** Граничный переход TFC → Destroy; исходный тип TFC и его глобальные свойства не изменяются. */
public final class SaltWaterAdapter {
    private SaltWaterAdapter() {}

    public static boolean isSaltWater(FluidStack stack) {
        return !stack.isEmpty() && (stack.getFluid() == TFCFluids.SALT_WATER.getSource()
            || stack.getFluid() == TFCFluids.SALT_WATER.getFlowing());
    }

    /**
     * Сохраняет количество 1:1. Концентрации и ключ названия задаёт вызывающий рецепт, а не этот адаптер.
     * Возвращает пустой стек для любой другой жидкости, поэтому обратного бесплатного цикла здесь нет.
     */
    public static FluidStack toDestroyMixture(FluidStack input, float sodiumChloride, float water, String translationKey) {
        if (!isSaltWater(input) || sodiumChloride <= 0 || water <= 0) return FluidStack.EMPTY;
        LegacyMixture mixture = new LegacyMixture()
            .addMolecule(DestroyMolecules.SODIUM_ION, sodiumChloride)
            .addMolecule(DestroyMolecules.CHLORIDE, sodiumChloride)
            .addMolecule(DestroyMolecules.WATER, water);
        return MixtureFluid.of(input.getAmount(), mixture, translationKey);
    }
}
