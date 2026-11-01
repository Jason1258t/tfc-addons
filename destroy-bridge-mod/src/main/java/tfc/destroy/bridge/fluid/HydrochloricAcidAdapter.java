package tfc.destroy.bridge.fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;

/** Изотермическое поглощение чистого HCl водой без параллельных молекул и жидкостей. */
public final class HydrochloricAcidAdapter {
    private HydrochloricAcidAdapter() {}

    /**
     * Возвращает одну жидкую Destroy-смесь с равными количествами H⁺ и Cl⁻.
     * Другие газы, растворённые примеси и пустой вход не принимаются.
     */
    public static FluidStack absorb(FluidStack acidGas, FluidStack water) {
        if (!isPure(acidGas, DestroyMolecules.HYDROCHLORIC_ACID) || !isPure(water, DestroyMolecules.WATER)) {
            return FluidStack.EMPTY;
        }
        int amount = acidGas.getAmount() + water.getAmount();
        double acidMoles = concentration(acidGas, DestroyMolecules.HYDROCHLORIC_ACID) * acidGas.getAmount();
        double waterMoles = concentration(water, DestroyMolecules.WATER) * water.getAmount();
        float acidConcentration = (float) (acidMoles / amount);
        LegacyMixture input = LegacyMixture.mix(java.util.Map.of(mixture(acidGas), (double) acidGas.getAmount(),
            mixture(water), (double) water.getAmount()));
        LegacyMixture result = new LegacyMixture()
            .addMolecule(DestroyMolecules.PROTON, acidConcentration)
            .addMolecule(DestroyMolecules.CHLORIDE, acidConcentration)
            .addMolecule(DestroyMolecules.WATER, (float) (waterMoles / amount));
        result.setTemperature(input.getTemperature());
        return MixtureFluid.of(amount, result);
    }

    private static boolean isPure(FluidStack stack, LegacySpecies molecule) {
        return !stack.isEmpty() && mixture(stack).getContents(false).size() == 1
            && concentration(stack, molecule) > 0;
    }

    private static double concentration(FluidStack stack, LegacySpecies molecule) {
        return mixture(stack).getConcentrationOf(molecule);
    }

    private static LegacyMixture mixture(FluidStack stack) {
        return LegacyMixture.readNBT(stack.getOrDefault(DestroyDataComponents.MIXTURE, new net.minecraft.nbt.CompoundTag()));
    }
}
