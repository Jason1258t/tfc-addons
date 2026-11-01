package tfc.destroy.extras.etching;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;

/** Cu + 2 Fe(III) → Cu(II) + 2 Fe(II) в существующей Destroy-смеси. */
public final class EtchingChemistry {
    public static final String SPENT_TRANSLATION_KEY = "destroy_extras.fluid.spent_etchant";

    private EtchingChemistry() {}

    public static boolean canEtch(FluidStack input, int amount, float copperMoles) {
        return !input.isEmpty() && input.getAmount() >= amount && amount > 0 && copperMoles > 0
            && moles(input, DestroyMolecules.IRON_III) + 0.0001 >= 2.0 * copperMoles;
    }

    /** Сохраняет объём, температуру и все не участвующие в реакции компоненты входной смеси. */
    public static FluidStack spentEtchant(FluidStack input, int amount, float copperMoles) {
        if (!canEtch(input, amount, copperMoles)) return FluidStack.EMPTY;
        LegacyMixture result = mixture(input);
        float ferricChange = (float) (-2.0 * copperMoles / amount);
        result.addMolecule(DestroyMolecules.IRON_III, ferricChange);
        result.addMolecule(DestroyMolecules.IRON_II, -ferricChange);
        result.addMolecule(DestroyMolecules.COPPER_II, (float) (copperMoles / amount));
        return MixtureFluid.of(amount, result, SPENT_TRANSLATION_KEY);
    }

    public static double moles(FluidStack stack, petrolpark.mc.destroy.chemistry.legacy.LegacySpecies molecule) {
        return stack.isEmpty() ? 0 : mixture(stack).getConcentrationOf(molecule) * stack.getAmount();
    }

    private static LegacyMixture mixture(FluidStack stack) {
        CompoundTag tag = stack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag());
        return LegacyMixture.readNBT(tag.copy());
    }
}
