package tfc.destroy.extras.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import ee.extras.pcb.PcbItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;
import tfc.destroy.extras.etching.EtchingChemistry;
import tfc.destroy.extras.recipe.MembraneElectrolysisRecipe;

/** Survival-рецепты, реальное поглощение HCl и сохранение старого состояния в M6. */
final class M6Test {
    private static final BlockPos BATH = new BlockPos(0, -60, 20);
    private static final ResourceLocation SALT_WATER = DestroyExtras.rl("electrolysis/salt_water");
    private static int checks;
    private static int failures;

    private M6Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m6recipes").executes(M6Test::recipes))
            .then(Commands.literal("m6absorption").executes(M6Test::absorption))
            .then(Commands.literal("m6assert").executes(M6Test::assertAbsorption))
            .then(Commands.literal("m6zeolite").executes(M6Test::zeolite))
            .then(Commands.literal("m6south").executes(M6Test::southRejectsImpurities)));
    }

    private static int zeolite(CommandContext<CommandSourceStack> ctx) {
        reset();
        var level = ctx.getSource().getLevel();
        var manager = level.getRecipeManager();
        RecipeHolder<?> natural = manager.byKey(ResourceLocation.parse("destroy_extras:survival/zeolite_natural_mixing"))
            .orElse(null);
        RecipeHolder<?> crafting = manager.byKey(ResourceLocation.parse("destroy_extras:survival/zeolite_natural_crafting"))
            .orElse(null);
        RecipeHolder<?> chemical = manager.byKey(ResourceLocation.parse("destroy_extras:survival/zeolite_chemical"))
            .orElse(null);
        check(ctx, natural != null && crafting != null && chemical != null, "оба рецепта цеолита загружены, ещё один — ручной");
        if (natural != null && natural.value() instanceof ProcessingRecipe<?, ?> recipe) {
            check(ctx, recipe.getIngredients().get(0).test(stack("tfc:rock/loose/tuff")),
                "природный рецепт принимает камешек туфа TFC");
            check(ctx, recipe.getIngredients().get(2).test(stack("destroy:silica")), "природный рецепт принимает кремнезём");
        } else {
            check(ctx, false, "природный рецепт — не Create-процесс");
        }
        if (chemical != null && chemical.value() instanceof ProcessingRecipe<?, ?> recipe) {
            check(ctx, recipe.getRequiredHeat() == HeatCondition.HEATED, "химический рецепт требует нагрев");
            check(ctx, recipe.getIngredients().get(0).test(stack("tfc:powder/kaolinite")),
                "химический рецепт принимает каолинитовый порошок TFC");
            RecipeHolder<?> salt = manager.byKey(SALT_WATER).orElse(null);
            if (salt != null && salt.value() instanceof MembraneElectrolysisRecipe electrolysis) {
                FluidStack naoh = electrolysis.products()[2];
                check(ctx, recipe.getFluidIngredients().getFirst().test(naoh),
                    "химический рецепт принимает настоящий NaOH из электролизёра M2");
            } else {
                check(ctx, false, "JSON-рецепт электролиза M2 не загружен");
            }
        } else {
            check(ctx, false, "химический рецепт — не Create-процесс");
        }
        return report(ctx);
    }

    private static int southRejectsImpurities(CommandContext<CommandSourceStack> ctx) {
        reset();
        ctx.getSource().getLevel().setBlock(BATH, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState(),
            Block.UPDATE_ALL);
        EtchingBathBlockEntity bath = bath(ctx);
        IFluidHandler acidPort = Capabilities.FluidHandler.BLOCK.getCapability(
            ctx.getSource().getLevel(), BATH, bath.getBlockState(), bath, Direction.SOUTH);
        LegacyMixture impure = new LegacyMixture()
            .addMolecule(DestroyMolecules.HYDROCHLORIC_ACID, DestroyMolecules.HYDROCHLORIC_ACID.getPureConcentration())
            .addMolecule(DestroyMolecules.WATER, DestroyMolecules.WATER.getPureConcentration());
        check(ctx, acidPort != null && acidPort.fill(MixtureFluid.of(25, impure), IFluidHandler.FluidAction.EXECUTE) == 0,
            "южный порт отказывает смеси HCl с водой");
        check(ctx, bath.hydrochloricAcid.isEmpty(), "отказ не оставляет примесей в баке HCl");
        check(ctx, acidPort != null && acidPort.fill(MixtureFluid.of(25, new LegacyMixture().addMolecule(
            DestroyMolecules.HYDROCHLORIC_ACID, DestroyMolecules.HYDROCHLORIC_ACID.getPureConcentration())),
            IFluidHandler.FluidAction.EXECUTE) == 25, "чистый HCl по-прежнему принимается");
        return report(ctx);
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static int recipes(CommandContext<CommandSourceStack> ctx) {
        reset();
        String[] ids = {
            "destroy_extras:survival/membrane_electrolyzer", "destroy_extras:survival/membrane",
            "destroy_extras:survival/etching_bath", "ee_extras:l1_board_blank", "ee_extras:l1_mask"
        };
        boolean ie = net.neoforged.fml.ModList.get().isLoaded("immersiveengineering");
        for (String id : ids) {
            RecipeHolder<?> holder = ctx.getSource().getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse(id)).orElse(null);
            boolean needsIe = !id.equals("destroy_extras:survival/etching_bath") && !id.equals("ee_extras:l1_mask");
            check(ctx, holder != null || needsIe && !ie,
                "Survival-рецепт " + (needsIe && !ie ? "корректно отключён без IE: " : "загружен: ") + id);
        }
        RecipeHolder<?> mask = ctx.getSource().getLevel().getRecipeManager()
            .byKey(ResourceLocation.parse("ee_extras:l1_mask")).orElse(null);
        if (mask != null && mask.value() instanceof net.minecraft.world.item.crafting.ShapelessRecipe shaped) {
            ItemStack result = shaped.getResultItem(ctx.getSource().getLevel().registryAccess());
            check(ctx, result.get(PcbItems.PCB_TOPOLOGY.get()) != null,
                "ручная маска получает настоящий компонент топологии L1");
        }
        return report(ctx);
    }

    private static int absorption(CommandContext<CommandSourceStack> ctx) {
        reset();
        ctx.getSource().getLevel().setBlock(BATH, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState(),
            Block.UPDATE_ALL);
        EtchingBathBlockEntity bath = bath(ctx);
        LegacyMixture acid = new LegacyMixture().addMolecule(DestroyMolecules.HYDROCHLORIC_ACID,
            DestroyMolecules.HYDROCHLORIC_ACID.getPureConcentration());
        IFluidHandler acidPort = Capabilities.FluidHandler.BLOCK.getCapability(
            ctx.getSource().getLevel(), BATH, bath.getBlockState(), bath, Direction.SOUTH);
        IFluidHandler waterPort = Capabilities.FluidHandler.BLOCK.getCapability(
            ctx.getSource().getLevel(), BATH, bath.getBlockState(), bath, Direction.EAST);
        check(ctx, acidPort != null && acidPort.fill(MixtureFluid.of(25, acid), IFluidHandler.FluidAction.EXECUTE) == 25,
            "южный capability принимает чистый HCl");
        check(ctx, waterPort != null && waterPort.fill(new FluidStack(Fluids.WATER, 250),
            IFluidHandler.FluidAction.EXECUTE) == 250, "восточный capability принимает обычную воду");
        check(ctx, bath.input.isEmpty() && bath.hydrochloricAcid.getFluidAmount() == 25
            && bath.water.getFluidAmount() == 250, "до тика входы раздельны, кислота ещё не создана");
        return report(ctx);
    }

    private static int assertAbsorption(CommandContext<CommandSourceStack> ctx) {
        reset();
        EtchingBathBlockEntity bath = bath(ctx);
        check(ctx, bath.hydrochloricAcid.isEmpty() && bath.water.isEmpty(), "HCl и вода израсходованы атомарно");
        check(ctx, bath.input.getFluidAmount() == 275, "получено 275 мБ штатной Destroy-смеси");
        check(ctx, close(EtchingChemistry.moles(bath.input.getFluid(), DestroyMolecules.PROTON), 1021.6676, 0.01)
            && close(EtchingChemistry.moles(bath.input.getFluid(), DestroyMolecules.CHLORIDE), 1021.6676, 0.01),
            "моли H⁺ и Cl⁻ соответствуют атомарной операции M3");
        check(ctx, close(EtchingChemistry.moles(bath.input.getFluid(), DestroyMolecules.WATER), 13873.4736, 0.01),
            "моли воды сохранены");
        check(ctx, bath.spent.isEmpty() && bath.batches == 0, "поглощение не создаёт отработанный раствор и не травит плату");
        CompoundTag saved = bath.saveWithoutMetadata(ctx.getSource().getLevel().registryAccess());
        check(ctx, saved.contains("HydrochloricAcid") && saved.contains("Water") && saved.contains("Input"),
            "все три производственных бака входят в сохранение старого/нового мира");
        return report(ctx);
    }

    private static EtchingBathBlockEntity bath(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getLevel().getBlockEntity(BATH) instanceof EtchingBathBlockEntity bath) return bath;
        throw new IllegalStateException("На позиции стенда M6 нет травильной ванны");
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static void reset() {
        checks = 0;
        failures = 0;
    }

    private static void check(CommandContext<CommandSourceStack> ctx, boolean condition, String label) {
        checks++;
        if (!condition) failures++;
        ctx.getSource().sendSystemMessage(Component.literal("DESTROYEXTRATEST " + (condition ? "OK " : "FAIL ") + label));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        int passed = checks - failures;
        ctx.getSource().sendSystemMessage(Component.literal(
            "DESTROYEXTRATEST " + (failures == 0 && checks > 0 ? "PASS " : "FAIL ") + passed + "/" + checks));
        return failures == 0 && checks > 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
