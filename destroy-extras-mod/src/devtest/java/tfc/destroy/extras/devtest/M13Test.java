package tfc.destroy.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.metallurgy.MetallurgicalProcessorBlockEntity;
import tfc.destroy.extras.metallurgy.MetallurgyStatus;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;
import tfc.destroy.extras.recipe.MetallurgyRecipe;

/** M13: реальные JSON-партии, EE, блокировка, просадка и restart универсального процессора. */
final class M13Test {
    private static final BlockPos PROCESS = new BlockPos(30, -60, 10);
    private static final BlockPos BATTERY = PROCESS.east(2);
    private static final BlockPos SOURCE = PROCESS.west(2);
    private static int checks;
    private static int failures;

    private M13Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m13environment").executes(M13Test::environment))
            .then(Commands.literal("m13build").executes(M13Test::build))
            .then(Commands.literal("m13load").then(Commands.argument("case", StringArgumentType.word())
                .executes(c -> load(c, StringArgumentType.getString(c, "case")))))
            .then(Commands.literal("m13power").then(Commands.argument("volts", DoubleArgumentType.doubleArg(0))
                .executes(c -> power(c, DoubleArgumentType.getDouble(c, "volts")))))
            .then(Commands.literal("m13restartcase").executes(M13Test::restartCase))
            .then(Commands.literal("m13report").executes(c -> {
                MetallurgicalProcessorBlockEntity p = process(c.getSource().getLevel());
                say(c, "M13 " + p.status() + " recipe=" + p.recipeId() + " progress=" + p.progress + " batches=" + p.batches
                    + " input=" + p.input.getFluidAmount() + " output=" + p.output.getFluidAmount() + "/" + p.secondaryOutput.getFluidAmount()
                    + " V=" + (p.device() == null ? 0 : p.device().measuredVoltage()) + " A=" + (p.device() == null ? 0 : p.device().measuredCurrent()));
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("m13assert").then(Commands.argument("case", StringArgumentType.word())
                .executes(c -> assertCase(c, StringArgumentType.getString(c, "case"))))));
    }

    private static int environment(CommandContext<CommandSourceStack> c) {
        reset();
        var recipes = c.getSource().getLevel().getRecipeManager().getAllRecipesFor(ExtrasRecipeTypes.METALLURGY_TYPE.get());
        check(c, recipes.size() == 23, "загружены 23 рецепта химической металлургии");
        for (RecipeHolder<MetallurgyRecipe> holder : recipes) {
            MetallurgyRecipe r = holder.value();
            check(c, r.voltage() == 240 || r.voltage() == 600, holder.id() + ": допустимый номинал EE");
        }
        check(c, recipe(c.getSource().getLevel(), "electrowin_copper").getFluidResults().getFirst().getAmount() == 5,
            "одна пыль даёт 5 мБ, базовая партия 20 — 100 мБ меди");
        check(c, recipe(c.getSource().getLevel(), "hall_heroult").catalyst().equals("tfc:ore/cryolite"),
            "Hall–Héroult требует постоянный криолитовый электролит");
        FluidStack copperSolution = recipe(c.getSource().getLevel(), "leach_malachite").getFluidResults().getFirst();
        LegacyMixture copperMixture = LegacyMixture.readNBT(copperSolution.getOrDefault(
            petrolpark.mc.destroy.DestroyDataComponents.MIXTURE, new net.minecraft.nbt.CompoundTag()));
        check(c, recipe(c.getSource().getLevel(), "electrowin_copper").getFluidIngredients().getFirst().test(copperSolution),
            "медный раствор соответствует рецепту электроэкстракции; Cu/S=" + copperMixture.getConcentrationOf(DestroyMolecules.COPPER_II)
                + "/" + copperMixture.getConcentrationOf(DestroyMolecules.SULFATE));
        MetallurgyRecipe nickel = recipe(c.getSource().getLevel(), "precipitate_nickel");
        check(c, nickel.getFluidIngredients().get(0).test(recipe(c.getSource().getLevel(), "leach_garnierite").getFluidResults().getFirst())
                && nickel.getFluidIngredients().get(1).test(recipe(c.getSource().getLevel(), "dissolve_nickel_sulfide").getFluidResults().get(1)),
            "никелевый раствор и сульфидный реагент соответствуют осаждению");
        MetallurgyRecipe regeneration = recipe(c.getSource().getLevel(), "regenerate_etchant");
        FluidStack spent = MixtureFluid.of(250, new LegacyMixture().addMolecule(DestroyMolecules.IRON_II, 6)
            .addMolecule(DestroyMolecules.COPPER_II, 3).addMolecule(DestroyMolecules.CHLORIDE, 18)
            .addMolecule(DestroyMolecules.WATER, 20));
        FluidStack chlorine = MixtureFluid.of(50, new LegacyMixture().addMolecule(DestroyMolecules.CHLORINE, 1));
        check(c, regeneration.getFluidIngredients().get(0).test(spent) && regeneration.getFluidIngredients().get(1).test(chlorine),
            "регенерация принимает отработанный FeCl₂/CuCl₂ и обязательный хлор");
        FluidStack aquaRegia = MixtureFluid.of(250, new LegacyMixture().addMolecule(DestroyMolecules.PROTON, 4)
            .addMolecule(DestroyMolecules.CHLORIDE, 3).addMolecule(DestroyMolecules.NITRATE, 1)
            .addMolecule(DestroyMolecules.WATER, 10));
        check(c, recipe(c.getSource().getLevel(), "precious_metals_recovery").getFluidIngredients().getFirst().test(aquaRegia),
            "кислотно-нитратное условие принимает царскую водку");
        return report(c);
    }

    private static int build(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        place(level, PROCESS, DestroyExtras.METALLURGY_BLOCK.get().defaultBlockState());
        place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        place(level, SOURCE, DestroyExtrasTest.SOURCE_BLOCK.get().defaultBlockState());
        wire(level, BATTERY, 0, PROCESS, 0);
        wire(level, BATTERY, 1, PROCESS, 1);
        power(level, 600);
        check(c, level.getBlockEntity(PROCESS) instanceof MetallurgicalProcessorBlockEntity, "процессор и EE-узлы установлены");
        return report(c);
    }

    private static int load(CommandContext<CommandSourceStack> c, String testCase) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        clear(level);
        power(level, switch (testCase) {
            case "leach", "blocked", "bayer" -> 240;
            default -> 600;
        });
        switch (testCase) {
            case "leach", "blocked" -> {
                process(level).items.setStackInSlot(0, stack("tfc:powder/malachite", 1));
                process(level).input.setFluid(mixture(250, 2, 1, 10));
                if (testCase.equals("blocked")) process(level).output.setFluid(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 8000));
            }
            case "electrowin" -> {
                FluidStack solution = recipe(level, "leach_malachite").getFluidResults().getFirst().copy();
                solution.setAmount(5000);
                process(level).input.setFluid(solution);
            }
            case "sag", "persisted" -> process(level).input.setFluid(recipe(level, "leach_malachite").getFluidResults().getFirst().copy());
            case "bayer" -> {
                process(level).items.setStackInSlot(0, stack("tfc_ie_addon:powder/bauxite", 1));
                LegacyMixture m = new LegacyMixture().addMolecule(DestroyMolecules.SODIUM_ION, 1)
                    .addMolecule(DestroyMolecules.HYDROXIDE, 1).addMolecule(DestroyMolecules.WATER, 4);
                process(level).input.setFluid(MixtureFluid.of(250, m));
            }
            case "hall" -> {
                process(level).items.setStackInSlot(0, new ItemStack(DestroyExtras.ALUMINA.get(), 20));
                process(level).items.setStackInSlot(1, stack("tfc:powder/coke", 20));
                process(level).items.setStackInSlot(2, stack("tfc:ore/cryolite", 1));
            }
            default -> check(c, false, "неизвестный сценарий " + testCase);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int assertCase(CommandContext<CommandSourceStack> c, String testCase) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        MetallurgicalProcessorBlockEntity p = process(level);
        switch (testCase) {
            case "leach" -> {
                check(c, p.batches == 1 && p.items.getStackInSlot(0).isEmpty(), "малахит израсходован одной партией");
                check(c, p.items.getStackInSlot(3).is(DestroyExtras.GANGUE.get()), "пустая порода выделена отдельно");
                check(c, p.output.getFluidAmount() == 250 && p.output.getFluid().getComponents().toString().contains("copper_ii"),
                    "получено 250 мБ медного раствора по JSON");
            }
            case "electrowin" -> {
                check(c, p.batches == 1 && p.output.getFluidAmount() == 5 && p.output.getFluid().getFluid().toString().contains("copper"),
                    "электролиз выдал 5 мБ существующей TFC-меди");
                check(c, p.secondaryOutput.getFluidAmount() == 200, "200 мБ кислоты возвращены в контур");
                check(c, p.device() != null && p.device().hasWorkingPower() && close(p.device().measuredVoltage(), 600, 0.3)
                    && close(p.device().measuredCurrent(), 8, 0.1), "электроэкстракция работает при 600 В / 8 А");
            }
            case "bayer" -> {
                check(c, p.batches == 1 && p.items.getStackInSlot(3).is(DestroyExtras.RED_MUD.get()), "Bayer выделяет красный шлам");
                check(c, p.output.getFluidAmount() == 250, "алюминатный раствор сохраняет объём 250 мБ");
            }
            case "hall" -> {
                check(c, p.batches == 1 && p.output.getFluidAmount() == 5, "одна глинозёмная партия даёт 5 мБ алюминия");
                check(c, p.items.getStackInSlot(0).getCount() == 19 && p.items.getStackInSlot(1).getCount() == 19,
                    "глинозём и углерод расходуются строго по одному");
                check(c, p.items.getStackInSlot(2).getCount() == 1 && p.secondaryOutput.getFluidAmount() == 50,
                    "криолит сохранён, анодные газы выведены");
                check(c, p.device() != null && close(p.device().measuredCurrent(), 16, 0.2), "ванна потребляет 16 А при 600 В");
            }
            case "blocked" -> {
                check(c, p.status() == MetallurgyStatus.BLOCKED_OUTPUT && p.batches == 0, "полный выход блокирует всю партию");
                check(c, p.items.getStackInSlot(0).getCount() == 1 && p.input.getFluidAmount() == 250, "входы не потеряны при блокировке");
            }
            case "sag_settled" -> {
                check(c, p.progress > 0, "перед просадкой есть незавершённый прогресс");
                power(level, 200);
                source(level).savedM4.putInt("Progress", p.progress);
                source(level).setChanged();
            }
            case "sag" -> {
                int progressDelta = p.progress - source(level).savedM4.getInt("Progress");
                check(c, p.status() == MetallurgyStatus.WAITING_FOR_POWER && progressDelta >= 0 && progressDelta <= 1,
                    "200 В останавливает процесс после не более чем одного переходного тика");
                check(c, p.batches == 0 && p.input.getFluidAmount() == 250, "просадка не расходует раствор");
            }
            case "persisted" -> {
                check(c, source(level).savedM4.getInt("Progress") > 0 && p.progress == source(level).savedM4.getInt("Progress"),
                    "restart восстановил точный прогресс");
                check(c, p.batches == 0 && p.input.getFluidAmount() == 250, "restart не повторил и не потерял партию");
            }
            default -> check(c, false, "неизвестная проверка " + testCase);
        }
        return report(c);
    }

    private static int restartCase(CommandContext<CommandSourceStack> c) {
        reset();
        MetallurgicalProcessorBlockEntity p = process(c.getSource().getLevel());
        check(c, p.progress > 0 && p.progress < 300, "перед restart есть незавершённая партия");
        source(c.getSource().getLevel()).savedM4.putInt("Progress", p.progress);
        source(c.getSource().getLevel()).setChanged();
        power(c.getSource().getLevel(), 0);
        return report(c);
    }

    private static FluidStack mixture(int amount, float protons, float sulfate, float water) {
        LegacyMixture m = new LegacyMixture().addMolecule(DestroyMolecules.PROTON, protons)
            .addMolecule(DestroyMolecules.SULFATE, sulfate).addMolecule(DestroyMolecules.WATER, water);
        return MixtureFluid.of(amount, m);
    }

    private static MetallurgyRecipe recipe(ServerLevel level, String name) {
        return (MetallurgyRecipe) level.getRecipeManager().byKey(DestroyExtras.rl("metallurgy/" + name)).orElseThrow().value();
    }

    private static ItemStack stack(String id, int count) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count); }
    private static MetallurgicalProcessorBlockEntity process(ServerLevel level) { return (MetallurgicalProcessorBlockEntity) level.getBlockEntity(PROCESS); }
    private static TestSourceBlockEntity source(ServerLevel level) { return (TestSourceBlockEntity) level.getBlockEntity(SOURCE); }
    private static void clear(ServerLevel level) {
        MetallurgicalProcessorBlockEntity p = process(level);
        for (int i = 0; i < 5; i++) p.items.setStackInSlot(i, ItemStack.EMPTY);
        p.input.setFluid(FluidStack.EMPTY); p.reagent.setFluid(FluidStack.EMPTY); p.output.setFluid(FluidStack.EMPTY); p.secondaryOutput.setFluid(FluidStack.EMPTY);
        p.progress = 0; p.batches = 0;
    }
    private static int power(CommandContext<CommandSourceStack> c, double volts) { power(c.getSource().getLevel(), volts); return Command.SINGLE_SUCCESS; }
    private static void power(ServerLevel level, double volts) {
        CreativeBatteryDevice b = DevicesSavedData.load(level).getDevice(BATTERY, CreativeBatteryDevice.class);
        b.voltage = volts; b.acFrequency = 0; DevicesSavedData.load(level).setDirty();
    }
    private static void wire(ServerLevel level, BlockPos a, int na, BlockPos b, int nb) {
        for (BlockPos pos : new BlockPos[] {a, b}) if (level.getBlockState(pos).getBlock() instanceof ElectricalDeviceBlock<?> block) block.ensureNodesExist(level, pos, level.getBlockState(pos));
        InfrastructureSavedData.load(level).connect(new InWorldNode(na, a), new InWorldNode(nb, b), CEEWireTypes.COPPER.get());
    }
    private static void place(ServerLevel level, BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_ALL); }
    private static boolean close(double a, double b, double t) { return Math.abs(a - b) <= t; }
    private static void reset() { checks = 0; failures = 0; }
    private static void say(CommandContext<CommandSourceStack> c, String text) { c.getSource().sendSystemMessage(Component.literal(text)); }
    private static void check(CommandContext<CommandSourceStack> c, boolean ok, String label) {
        checks++; if (!ok) failures++;
        say(c, "DESTROYEXTRATEST " + (ok ? "OK " : "FAIL ") + label);
    }
    private static int report(CommandContext<CommandSourceStack> c) {
        say(c, "DESTROYEXTRATEST " + (failures == 0 && checks > 0 ? "PASS " : "FAIL ") + (checks - failures) + "/" + checks);
        return failures == 0 && checks > 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
