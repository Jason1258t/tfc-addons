package tfc.destroy.extras.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlock;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;

/** Проверки нефтехимии и полимеров пункта 13 на штатном Destroy Vat. */
final class M9Test {
    private static final BlockPos VAT = new BlockPos(40, -60, 11);
    private static int checks;
    private static int failures;

    private M9Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m9build").executes(M9Test::build))
            .then(Commands.literal("m9prepare").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> prepare(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m9assert").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> assertCase(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m9recipes").executes(M9Test::recipes)));
    }

    private static int build(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(VAT.offset(-2, -2, -4), VAT.offset(2, 2, 0))) {
            boolean inside = Math.abs(pos.getX() - VAT.getX()) <= 1 && Math.abs(pos.getY() - VAT.getY()) <= 1
                && pos.getZ() >= VAT.getZ() - 3 && pos.getZ() < VAT.getZ();
            level.setBlock(pos, inside ? Blocks.AIR.defaultBlockState() : Blocks.NETHERITE_BLOCK.defaultBlockState(),
                Block.UPDATE_ALL);
        }
        level.setBlock(VAT, DestroyBlocks.VAT_CONTROLLER.getDefaultState()
            .setValue(VatControllerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        VatControllerBlockEntity vat = vat(level);
        if (vat.getVatOptional().isEmpty()) vat.tryMakeVat();
        check(ctx, vat.getVatOptional().isPresent(), "штатный Vat 3×3×3 сформирован");
        for (String id : new String[] {
            "destroy:ethene_polymerization", "destroy:propene_polymerization", "destroy:chloroethene_polymerization",
            "destroy:styrene_butadiene_copolymerization", "destroy:benzene_ethylation",
            "destroy:ethylbenzene_dehydrogenation", "destroy:cumene_process"
        }) {
            check(ctx, LegacyReaction.REACTIONS.containsKey(id), "загружена штатная реакция " + id);
        }
        RecipeHolder<?> distillation = level.getRecipeManager().byKey(ResourceLocation.parse("destroy:distillation/crude_oil"))
            .orElse(null);
        check(ctx, distillation != null, "штатная перегонка Destroy принимает нефть и выдаёт заводской газ с мономерами");
        return report(ctx);
    }

    private static int prepare(CommandContext<CommandSourceStack> ctx, String testCase) {
        VatControllerBlockEntity vat = vat(ctx.getSource().getLevel());
        clear(vat);
        switch (testCase) {
            case "pe" -> setLiquid(vat, Map.of(DestroyMolecules.ETHENE, 0.01, DestroyMolecules.AIBN, 0.001));
            case "pp" -> setLiquid(vat, Map.of(DestroyMolecules.PROPENE, 0.01, DestroyMolecules.AIBN, 0.001));
            case "pvc" -> setLiquid(vat, Map.of(DestroyMolecules.CHLOROETHENE, 0.01, DestroyMolecules.AIBN, 0.001));
            case "sbr" -> setLiquid(vat, Map.of(DestroyMolecules.STYRENE, 0.1, DestroyMolecules.BUTADIENE, 0.1,
                DestroyMolecules.AIBN, 0.01));
            case "pe_no_aibn" -> setLiquid(vat, Map.of(DestroyMolecules.ETHENE, 0.01));
            case "styrene" -> setLiquid(vat, Map.of(DestroyMolecules.BENZENE, 0.01, DestroyMolecules.ETHENE, 0.01,
                DestroyMolecules.PROTON, 0.001, DestroyMolecules.WATER, 0.02, DestroyMolecules.IRON_III, 0.001));
            case "cumene" -> setGas(vat, Map.of(DestroyMolecules.BENZENE, 0.01, DestroyMolecules.PROPENE, 0.01,
                DestroyMolecules.OXYGEN, 0.01, DestroyMolecules.AIBN, 0.001, DestroyMolecules.PROTON, 0.001));
            default -> {
                ctx.getSource().sendFailure(Component.literal("неизвестный сценарий M9: " + testCase));
                return 0;
            }
        }
        vat.updateCachedMixture();
        vat.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("подготовлен сценарий M9 " + testCase), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assertCase(CommandContext<CommandSourceStack> ctx, String testCase) {
        reset();
        VatControllerBlockEntity vat = vat(ctx.getSource().getLevel());
        FluidStack gas = vat.getGasTankContents();
        FluidStack liquid = vat.getLiquidTankContents();
        switch (testCase) {
            case "pe" -> checkPolymer(ctx, vat, DestroyItems.POLYETHENE.get(), "этен осаждается в штатный PE Destroy");
            case "pp" -> checkPolymer(ctx, vat, DestroyItems.POLYPROPENE.get(), "пропен осаждается в штатный PP Destroy");
            case "pvc" -> checkPolymer(ctx, vat, DestroyItems.POLYVINYL_CHLORIDE.get(), "хлорэтен осаждается в штатный PVC Destroy");
            case "sbr" -> checkPolymer(ctx, vat, DestroyItems.POLYSTYRENE_BUTADIENE.get(),
                "стирол и бутадиен осаждаются в штатный SBR Destroy");
            case "pe_no_aibn" -> {
                check(ctx, count(vat, DestroyItems.POLYETHENE.get()) == 0, "без AIBN полиэтилен не осаждается");
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.ETHENE) > 0, "мономер сохранён без инициатора");
            }
            case "styrene" -> {
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.STYRENE) > 0,
                    "бензол и этен дают этилбензол, который дегидрируется в стирол");
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.HYDROGEN) > 0,
                    "дегидрирование этилбензола возвращает водород в общую сеть");
            }
            case "cumene" -> {
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.PHENOL) > 0,
                    "кумольный процесс Destroy производит фенол");
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.ACETONE) > 0,
                    "кумольный процесс одновременно производит ацетон");
            }
            default -> check(ctx, false, "неизвестный сценарий M9 " + testCase);
        }
        ctx.getSource().sendSystemMessage(Component.literal("M9 gas=" + contents(gas) + ", liquid=" + contents(liquid)));
        return report(ctx);
    }

    private static int recipes(CommandContext<CommandSourceStack> ctx) {
        reset();
        TagKey<Item> rubber = TagKey.create(Registries.ITEM, ResourceLocation.parse("tfc:rubber"));
        TagKey<Item> insulation = TagKey.create(Registries.ITEM, ResourceLocation.parse("tfc:insulation"));
        Ingredient rubberIngredient = Ingredient.of(rubber);
        Ingredient insulationIngredient = Ingredient.of(insulation);
        boolean afc = net.neoforged.fml.ModList.get().isLoaded("afc");
        check(ctx, rubberIngredient.test(new ItemStack(DestroyItems.POLYSTYRENE_BUTADIENE.get()))
            && (!afc || rubberIngredient.test(item("afc:rubber_bar"))),
            "общий тег резины принимает SBR" + (afc ? " и натуральную резину AFC" : " без обязательной зависимости от AFC"));
        for (Item item : new Item[] {
            DestroyItems.POLYETHENE.get(), DestroyItems.POLYPROPENE.get(), DestroyItems.POLYVINYL_CHLORIDE.get(),
            DestroyItems.POLYSTYRENE_BUTADIENE.get()
        }) {
            check(ctx, insulationIngredient.test(new ItemStack(item)), "тег изоляции принимает " + BuiltInRegistries.ITEM.getKey(item));
        }
        for (String id : new String[] {
            "create:crafting/kinetics/belt_connector", "tfc_plans:create/hose_pulley_rubber",
            "tfc_plans:create/belt_connector_pressing_rubber", "electroenergetics:crafting/insulated_wire",
            "electroenergetics:crafting/heavily_insulated_wire"
        }) {
            check(ctx, ctx.getSource().getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent(),
                "рецепт-потребитель загружен: " + id);
        }
        boolean ie = net.neoforged.fml.ModList.get().isLoaded("immersiveengineering");
        check(ctx, !ie || ctx.getSource().getLevel().getRecipeManager()
            .byKey(ResourceLocation.parse("immersiveengineering:refinery/resin")).isPresent(),
            "единственная фенольная смола IE и её существующие потребители сохранены");
        return report(ctx);
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static void checkPolymer(CommandContext<CommandSourceStack> ctx, VatControllerBlockEntity vat, Item item,
        String label) {
        check(ctx, count(vat, item) > 0, label);
        check(ctx, molesBoth(vat.getGasTankContents(), vat.getLiquidTankContents(), DestroyMolecules.AIBN) > 0,
            "инициатор AIBN остаётся в смеси и не расходуется как мономер");
    }

    private static int count(VatControllerBlockEntity vat, Item item) {
        int count = 0;
        for (int slot = 0; slot < vat.inventory.getSlots(); slot++) {
            ItemStack stack = vat.inventory.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void clear(VatControllerBlockEntity vat) {
        vat.getGasTank().setFluid(FluidStack.EMPTY);
        vat.getLiquidTank().setFluid(FluidStack.EMPTY);
        for (int slot = 0; slot < vat.inventory.getSlots(); slot++) vat.inventory.extractItem(slot, 64, false);
    }

    private static void setGas(VatControllerBlockEntity vat, Map<LegacySpecies, Double> molecules) {
        vat.getGasTank().setFluid(mixture(molecules));
    }

    private static void setLiquid(VatControllerBlockEntity vat, Map<LegacySpecies, Double> molecules) {
        LegacyMixture mixture = new LegacyMixture();
        molecules.forEach((molecule, concentration) -> mixture.addMolecule(molecule, concentration.floatValue()));
        mixture.heat((298.15F - mixture.getTemperature()) * mixture.getVolumetricHeatCapacity());
        vat.getLiquidTank().setFluid(MixtureFluid.of(vat.getCapacity(), mixture));
    }

    private static FluidStack mixture(Map<LegacySpecies, Double> molecules) {
        LegacyMixture mixture = new LegacyMixture();
        molecules.forEach((molecule, concentration) -> mixture.addMolecule(molecule, concentration.floatValue()));
        mixture.heat((298.15F - mixture.getTemperature()) * mixture.getVolumetricHeatCapacity());
        return MixtureFluid.of(1000, mixture);
    }

    private static double molesBoth(FluidStack gas, FluidStack liquid, LegacySpecies molecule) {
        return moles(gas, molecule) + moles(liquid, molecule);
    }

    private static double moles(FluidStack stack, LegacySpecies molecule) {
        return stack.isEmpty() ? 0 : mixture(stack).getConcentrationOf(molecule) * stack.getAmount();
    }

    private static LegacyMixture mixture(FluidStack stack) {
        return LegacyMixture.readNBT(stack.getOrDefault(DestroyDataComponents.MIXTURE, new net.minecraft.nbt.CompoundTag()));
    }

    private static String contents(FluidStack stack) {
        if (stack.isEmpty()) return "{}";
        StringBuilder result = new StringBuilder("{");
        for (LegacySpecies molecule : mixture(stack).getContents(false)) {
            if (result.length() > 1) result.append(", ");
            result.append(molecule.getFullID()).append('=').append(moles(stack, molecule));
        }
        return result.append('}').toString();
    }

    private static VatControllerBlockEntity vat(ServerLevel level) {
        if (level.getBlockEntity(VAT) instanceof VatControllerBlockEntity vat) return vat;
        throw new IllegalStateException("На позиции стенда нет штатного Vat M9");
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
