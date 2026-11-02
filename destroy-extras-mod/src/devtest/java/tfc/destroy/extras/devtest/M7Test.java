package tfc.destroy.extras.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugeBlockEntity;

/** Проверки штатной центрифуги Destroy и JSON-рецептов этапа после M6. */
final class M7Test {
    private static final BlockPos CENTRIFUGE = new BlockPos(20, -60, 10);
    private static int checks;
    private static int failures;

    private M7Test() {}

    static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
        if (event.getServer().overworld().getBlockEntity(CENTRIFUGE) instanceof CentrifugeBlockEntity centrifuge) {
            centrifuge.setSpeed(64);
        }
    }

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m7build").executes(M7Test::build))
            .then(Commands.literal("m7prepare").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> prepare(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m7assert").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> assertCase(ctx, StringArgumentType.getString(ctx, "case"))))));
    }

    private static int build(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        level.setBlock(CENTRIFUGE, DestroyBlocks.CENTRIFUGE.getDefaultState(), Block.UPDATE_ALL);
        centrifuge(level).setSpeed(64);
        for (String id : new String[] {
            "tfc_plans:centrifugation/olive_oil_water", "tfc_plans:centrifugation/canola_oil_water",
            "destroy:centrifugation/milk"
        }) {
            check(ctx, level.getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent(),
                "JSON-рецепт загружен: " + id);
        }
        return report(ctx);
    }

    private static int prepare(CommandContext<CommandSourceStack> ctx, String testCase) {
        CentrifugeBlockEntity centrifuge = centrifuge(ctx.getSource().getLevel());
        clear(centrifuge);
        switch (testCase) {
            case "olive" -> centrifuge.getInputTank().setFluid(fluid("tfc:olive_oil_water", 250));
            case "canola" -> centrifuge.getInputTank().setFluid(fluid("tfc:canola_oil_water", 250));
            case "milk" -> centrifuge.getInputTank().setFluid(fluid("minecraft:milk", 1000));
            case "mixture" -> {
                LegacyMixture acid = new LegacyMixture()
                    .addMolecule(DestroyMolecules.PROTON, 50.0F / 1000)
                    .addMolecule(DestroyMolecules.CHLORIDE, 50.0F / 1000)
                    .addMolecule(DestroyMolecules.WATER, 950.0F / 1000);
                acid.heat((298.15F - acid.getTemperature()) * acid.getVolumetricHeatCapacity());
                centrifuge.getInputTank().setFluid(MixtureFluid.of(1000, acid));
            }
            case "blocked" -> {
                centrifuge.getInputTank().setFluid(fluid("tfc:olive_oil_water", 250));
                centrifuge.getLightOutputTank().setFluid(fluid("tfc:olive_oil", centrifuge.getLightOutputTank().getCapacity()));
            }
            default -> {
                ctx.getSource().sendFailure(Component.literal("неизвестный сценарий M7: " + testCase));
                return 0;
            }
        }
        centrifuge.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("подготовлен сценарий M7 " + testCase), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assertCase(CommandContext<CommandSourceStack> ctx, String testCase) {
        reset();
        CentrifugeBlockEntity centrifuge = centrifuge(ctx.getSource().getLevel());
        FluidStack input = centrifuge.getInputTank().getFluid();
        FluidStack dense = centrifuge.getDenseOutputTank().getFluid();
        FluidStack light = centrifuge.getLightOutputTank().getFluid();
        switch (testCase) {
            case "olive", "canola" -> {
                String oil = testCase.equals("olive") ? "tfc:olive_oil" : "tfc:canola_oil";
                check(ctx, input.isEmpty(), "250 мБ эмульсии полностью израсходованы");
                check(ctx, fluidIs(dense, "minecraft:water") && dense.getAmount() == 200,
                    "тяжёлый выход — 200 мБ воды");
                check(ctx, fluidIs(light, oil) && light.getAmount() == 50, "лёгкий выход — 50 мБ масла");
                check(ctx, dense.getAmount() + light.getAmount() == 250, "жидкий массовый баланс 250 = 200 + 50");
                check(ctx, centrifuge.getSpeed() == 64, "процесс выполнен на номинальные 64 об/мин");
            }
            case "milk" -> {
                check(ctx, input.isEmpty(), "1000 мБ молока полностью израсходованы");
                check(ctx, fluidIs(dense, "firmalife:cream") && dense.getAmount() == 500,
                    "заменённый стоковый рецепт даёт 500 мБ сливок Firmalife");
                check(ctx, fluidIs(light, "destroy:skimmed_milk") && light.getAmount() == 500,
                    "лёгкий выход — 500 мБ штатного обезжиренного молока Destroy");
                check(ctx, dense.getAmount() + light.getAmount() == 1000, "жидкий массовый баланс 1000 = 500 + 500");
            }
            case "mixture" -> {
                check(ctx, input.isEmpty(), "1000 мБ Destroy-смеси полностью израсходованы");
                check(ctx, dense.getAmount() == 500 && light.getAmount() == 500,
                    "автоматическое разделение смеси даёт два равных объёма по 500 мБ");
                check(ctx, conserved(dense, light, DestroyMolecules.PROTON, 50), "моли H⁺ сохранены");
                check(ctx, conserved(dense, light, DestroyMolecules.CHLORIDE, 50), "моли Cl⁻ сохранены");
                check(ctx, conserved(dense, light, DestroyMolecules.WATER, 950), "моли H₂O сохранены");
            }
            case "blocked" -> {
                check(ctx, input.getAmount() == 250, "полный лёгкий выход не даёт списать вход");
                check(ctx, light.getAmount() == centrifuge.getLightOutputTank().getCapacity(), "лёгкий выход остаётся полным");
                check(ctx, dense.isEmpty(), "частичный тяжёлый продукт не создаётся");
            }
            default -> check(ctx, false, "неизвестный сценарий M7 " + testCase);
        }
        ctx.getSource().sendSystemMessage(Component.literal("M7 input=" + input.getAmount()
            + ", dense=" + dense.getAmount() + " " + id(dense)
            + ", light=" + light.getAmount() + " " + id(light)));
        return report(ctx);
    }

    private static boolean conserved(FluidStack dense, FluidStack light, LegacySpecies species, double expected) {
        return Math.abs(moles(dense, species) + moles(light, species) - expected) < 0.05;
    }

    private static double moles(FluidStack stack, LegacySpecies species) {
        if (stack.isEmpty()) return 0;
        LegacyMixture mixture = LegacyMixture.readNBT(stack.getOrDefault(DestroyDataComponents.MIXTURE,
            new net.minecraft.nbt.CompoundTag()));
        return mixture.getConcentrationOf(species) * stack.getAmount();
    }

    private static void clear(CentrifugeBlockEntity centrifuge) {
        centrifuge.getInputTank().setFluid(FluidStack.EMPTY);
        centrifuge.getDenseOutputTank().setFluid(FluidStack.EMPTY);
        centrifuge.getLightOutputTank().setFluid(FluidStack.EMPTY);
    }

    private static CentrifugeBlockEntity centrifuge(ServerLevel level) {
        if (level.getBlockEntity(CENTRIFUGE) instanceof CentrifugeBlockEntity centrifuge) return centrifuge;
        throw new IllegalStateException("На позиции стенда нет штатной центрифуги Destroy");
    }

    private static FluidStack fluid(String id, int amount) {
        return new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse(id)), amount);
    }

    private static boolean fluidIs(FluidStack stack, String id) {
        return !stack.isEmpty() && BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(ResourceLocation.parse(id));
    }

    private static String id(FluidStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
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
