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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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

/** Проверки серной и азотной веток пункта 12 на штатном Destroy Vat. */
final class M8Test {
    private static final BlockPos VAT = new BlockPos(30, -60, 11);
    private static final BlockPos SOURCE = new BlockPos(30, -60, 20);
    private static final ResourceLocation NICKEL_OSTWALD = ResourceLocation.parse("tfc_plans:ostwald_process_nickel");
    private static int checks;
    private static int failures;
    private static boolean thermostat;
    private static float targetTemperature = 298.15F;

    private M8Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m8build").executes(M8Test::build))
            .then(Commands.literal("m8prepare").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> prepare(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m8assert").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> assertCase(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m8restartcase").executes(M8Test::restartCase)));
    }

    static void tick(ServerTickEvent.Post event) {
        if (thermostat && event.getServer().overworld().getBlockEntity(VAT) instanceof VatControllerBlockEntity vat) {
            vat.changeHeatingPower(vat.getTemperature() > targetTemperature ? -30_000_000.0F
                : vat.getTemperature() < targetTemperature - 0.1F ? 30_000_000.0F : 0.0F);
        }
    }

    private static int build(CommandContext<CommandSourceStack> ctx) {
        reset();
        thermostat = false;
        ServerLevel level = ctx.getSource().getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(VAT.offset(-2, -2, -4), VAT.offset(2, 2, 0))) {
            boolean inside = Math.abs(pos.getX() - VAT.getX()) <= 1 && Math.abs(pos.getY() - VAT.getY()) <= 1
                && pos.getZ() >= VAT.getZ() - 3 && pos.getZ() < VAT.getZ();
            level.setBlock(pos, inside ? Blocks.AIR.defaultBlockState() : Blocks.NETHERITE_BLOCK.defaultBlockState(),
                Block.UPDATE_ALL);
        }
        level.setBlock(VAT, DestroyBlocks.VAT_CONTROLLER.getDefaultState()
            .setValue(VatControllerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        level.setBlock(SOURCE, DestroyExtrasTest.SOURCE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        VatControllerBlockEntity vat = vat(level);
        if (vat.getVatOptional().isEmpty()) vat.tryMakeVat();
        check(ctx, vat.getVatOptional().isPresent(), "штатный Vat 3×3×3 сформирован");
        for (String id : new String[] {
            "destroy:sulfur_oxidation", "destroy:contact_process", "destroy:sulfur_trioxide_hydration",
            "destroy:haber_process", "destroy:ostwald_process", NICKEL_OSTWALD.toString()
        }) {
            check(ctx, LegacyReaction.REACTIONS.containsKey(id), "загружена реакция " + id);
        }
        LegacyReaction nickel = LegacyReaction.REACTIONS.get(NICKEL_OSTWALD.toString());
        LegacyReaction rhodium = LegacyReaction.REACTIONS.get("destroy:ostwald_process");
        check(ctx, nickel != null && nickel.isDatapack(), "никелевый вариант загружен именно из datapack");
        check(ctx, nickel != null && rhodium != null
                && nickel.getPreexponentialFactor() < rhodium.getPreexponentialFactor(),
            "родиевый вариант сохранён и работает быстрее никелевого");
        return report(ctx);
    }

    private static int prepare(CommandContext<CommandSourceStack> ctx, String testCase) {
        VatControllerBlockEntity vat = vat(ctx.getSource().getLevel());
        clear(vat);
        thermostat = !testCase.equals("air") && !testCase.equals("persisted");
        targetTemperature = switch (testCase) {
            case "sulfur" -> 750.0F;
            case "contact", "haber" -> 700.0F;
            case "ostwald", "ostwald_nickel", "ostwald_no_catalyst" -> 400.0F;
            default -> 298.15F;
        };
        switch (testCase) {
            case "air" -> vat.flush();
            case "sulfur" -> setGas(vat, Map.of(DestroyMolecules.OCTASULFUR, 0.01, DestroyMolecules.OXYGEN, 0.08));
            case "contact", "contact_no_catalyst" -> {
                setGas(vat, Map.of(DestroyMolecules.SULFUR_DIOXIDE, 0.1, DestroyMolecules.OXYGEN, 0.05));
                if (testCase.equals("contact")) catalyst(vat, DestroyItems.PLATINUM_POWDER.get());
            }
            case "hydration" -> setLiquid(vat, Map.of(DestroyMolecules.SULFUR_TRIOXIDE, 0.1,
                DestroyMolecules.WATER, 0.2));
            case "haber" -> {
                setGas(vat, Map.of(DestroyMolecules.NITROGEN, 0.01, DestroyMolecules.HYDROGEN, 0.03));
                catalyst(vat, DestroyItems.IRON_POWDER.get());
            }
            case "ostwald", "ostwald_nickel", "ostwald_no_catalyst" -> {
                setGas(vat, Map.of(DestroyMolecules.AMMONIA, 0.01, DestroyMolecules.OXYGEN, 0.02));
                if (testCase.equals("ostwald")) catalyst(vat, DestroyItems.RHODIUM_POWDER.get());
                if (testCase.equals("ostwald_nickel")) {
                    Item nickel = BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersiveengineering:dust_nickel"));
                    if (nickel == net.minecraft.world.item.Items.AIR) {
                        ctx.getSource().sendFailure(Component.literal("В этом профиле нет никелевой пыли IE"));
                        return 0;
                    }
                    catalyst(vat, nickel);
                }
            }
            default -> {
                ctx.getSource().sendFailure(Component.literal("неизвестный сценарий M8: " + testCase));
                return 0;
            }
        }
        vat.updateCachedMixture();
        vat.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("подготовлен сценарий M8 " + testCase), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assertCase(CommandContext<CommandSourceStack> ctx, String testCase) {
        reset();
        VatControllerBlockEntity vat = vat(ctx.getSource().getLevel());
        FluidStack gas = vat.getGasTankContents();
        FluidStack liquid = vat.getLiquidTankContents();
        switch (testCase) {
            case "air" -> {
                double nitrogen = moles(gas, DestroyMolecules.NITROGEN);
                double oxygen = moles(gas, DestroyMolecules.OXYGEN);
                check(ctx, gas.getAmount() == 27000 && nitrogen > 0 && oxygen > 0,
                    "flush заполняет свободный объём чана воздухом без отдельной машины");
                check(ctx, close(nitrogen / oxygen, 0.78084 / 0.21916, 0.0001),
                    "состав воздуха — 78,084 % азота и 21,916 % кислорода");
            }
            case "sulfur" -> {
                double dioxide = molesBoth(gas, liquid, DestroyMolecules.SULFUR_DIOXIDE);
                check(ctx, dioxide > 0.001, "кислород воздуха окисляет расплав серы до SO₂");
                check(ctx, close(molesBoth(gas, liquid, DestroyMolecules.OCTASULFUR) + dioxide / 8, 10, 0.1),
                    "сера сохраняется: S₈ + 8 O₂ → 8 SO₂");
                check(ctx, close(80 - molesBoth(gas, liquid, DestroyMolecules.OXYGEN), dioxide, 0.1),
                    "на каждый SO₂ израсходован один O₂");
            }
            case "contact" -> {
                double trioxide = molesBoth(gas, liquid, DestroyMolecules.SULFUR_TRIOXIDE);
                check(ctx, trioxide > 0.001, "платиновый катализатор запускает контактный процесс");
                check(ctx, close(molesBoth(gas, liquid, DestroyMolecules.SULFUR_DIOXIDE) + trioxide, 100, 5),
                    "сера сохраняется с численной погрешностью фазового перехода Destroy до 5 %");
                check(ctx, close(50 - molesBoth(gas, liquid, DestroyMolecules.OXYGEN), trioxide / 2, 3),
                    "на два SO₃ израсходован один O₂");
                check(ctx, catalystKept(vat, DestroyItems.PLATINUM_POWDER.get()), "платиновый катализатор не расходуется");
            }
            case "contact_no_catalyst" -> {
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.SULFUR_TRIOXIDE) < 0.000001,
                    "без платины SO₃ не образуется");
                check(ctx, close(molesBoth(gas, liquid, DestroyMolecules.OXYGEN), 50, 0.00001),
                    "кислород не расходуется без платины");
            }
            case "hydration" -> {
                double acid = molesBoth(gas, liquid, DestroyMolecules.SULFURIC_ACID);
                double sulfur = molesBoth(gas, liquid, DestroyMolecules.SULFUR_TRIOXIDE) + acid
                    + molesBoth(gas, liquid, DestroyMolecules.HYDROGENSULFATE)
                    + molesBoth(gas, liquid, DestroyMolecules.SULFATE)
                    + 2 * molesBoth(gas, liquid, DestroyMolecules.OLEUM);
                check(ctx, acid > 1, "гидратация SO₃ даёт серную кислоту");
                check(ctx, close(sulfur, 100, 5), "сера сохраняется с учётом диссоциации и олеума Destroy");
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.HYDROGENSULFATE) > 0
                    && molesBoth(gas, liquid, DestroyMolecules.PROTON) > 0,
                    "штатная кислота частично диссоциирует, а не остаётся только H₂SO₄");
            }
            case "haber" -> {
                double ammonia = molesBoth(gas, liquid, DestroyMolecules.AMMONIA);
                check(ctx, ammonia > 0.001, "железный катализатор запускает процесс Габера");
                check(ctx, close(2 * molesBoth(gas, liquid, DestroyMolecules.NITROGEN) + ammonia, 20, 0.1),
                    "азот сохраняется: N₂ + 3 H₂ → 2 NH₃");
                check(ctx, close(30 - molesBoth(gas, liquid, DestroyMolecules.HYDROGEN), ammonia * 1.5, 0.1),
                    "на два NH₃ израсходованы три H₂");
                check(ctx, catalystKept(vat, DestroyItems.IRON_POWDER.get()), "железный катализатор не расходуется");
            }
            case "ostwald", "ostwald_nickel" -> {
                double acid = molesBoth(gas, liquid, DestroyMolecules.NITRIC_ACID);
                check(ctx, acid > 0.00001, testCase.equals("ostwald_nickel")
                    ? "никелевый катализатор открывает азотную кислоту без родия" : "родиевый катализатор запускает Оствальда");
                double fixedNitrogen = acid + molesBoth(gas, liquid, DestroyMolecules.NITRATE)
                    + molesBoth(gas, liquid, DestroyMolecules.AMMONIUM);
                double controlAmmonia = liquid.isEmpty() ? 10 : 13.32898235321045;
                double controlOxygen = liquid.isEmpty() ? 20 : 26.6579647064209;
                check(ctx, close(molesBoth(gas, liquid, DestroyMolecules.AMMONIA) + fixedNitrogen, controlAmmonia, 0.001),
                    "азот сохраняется относительно контрольной смеси в той же фазе");
                check(ctx, close(controlOxygen - molesBoth(gas, liquid, DestroyMolecules.OXYGEN), fixedNitrogen * 2, 0.001),
                    "на каждую HNO₃ израсходованы два O₂ относительно контрольной смеси");
                Item catalyst = testCase.equals("ostwald_nickel")
                    ? BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersiveengineering:dust_nickel"))
                    : DestroyItems.RHODIUM_POWDER.get();
                check(ctx, catalystKept(vat, catalyst), "катализатор Оствальда не расходуется");
            }
            case "ostwald_no_catalyst" -> {
                check(ctx, molesBoth(gas, liquid, DestroyMolecules.NITRIC_ACID) < 0.000001,
                    "без катализатора азотная кислота не образуется");
                double ammonia = molesBoth(gas, liquid, DestroyMolecules.AMMONIA);
                double oxygen = molesBoth(gas, liquid, DestroyMolecules.OXYGEN);
                check(ctx, close(oxygen, ammonia * 2, 0.000001),
                    "NH₃ и O₂ сохраняют исходное отношение 1:2 после фазового перехода Destroy");
            }
            case "persisted" -> {
                CompoundTag saved = source(ctx.getSource().getLevel()).savedM8;
                FluidStack savedGas = FluidStack.parseOptional(ctx.getSource().getLevel().registryAccess(), saved.getCompound("Gas"));
                check(ctx, gas.getAmount() == saved.getInt("GasAmount")
                    && close(moles(gas, DestroyMolecules.NITROGEN), moles(savedGas, DestroyMolecules.NITROGEN), 0.000001)
                    && close(moles(gas, DestroyMolecules.OXYGEN), moles(savedGas, DestroyMolecules.OXYGEN), 0.000001),
                    "состав газовой смеси восстановлен после пересчёта температуры Destroy");
                check(ctx, FluidStack.isSameFluidSameComponents(liquid,
                        FluidStack.parseOptional(ctx.getSource().getLevel().registryAccess(), saved.getCompound("Liquid")))
                    && liquid.getAmount() == saved.getInt("LiquidAmount"), "точная жидкая смесь восстановлена");
                check(ctx, ItemStack.matches(vat.inventory.getStackInSlot(0),
                    ItemStack.parseOptional(ctx.getSource().getLevel().registryAccess(), saved.getCompound("Catalyst"))),
                    "катализатор восстановлен без дублирования");
                check(ctx, LegacyReaction.REACTIONS.containsKey(NICKEL_OSTWALD.toString())
                    && LegacyReaction.REACTIONS.containsKey("destroy:ostwald_process"),
                    "после reload присутствуют обе реакции Оствальда без замены штатной");
            }
            default -> check(ctx, false, "неизвестный сценарий M8 " + testCase);
        }
        ctx.getSource().sendSystemMessage(Component.literal("M8 gas=" + contents(gas) + ", liquid=" + contents(liquid)));
        return report(ctx);
    }

    private static int restartCase(CommandContext<CommandSourceStack> ctx) {
        reset();
        thermostat = false;
        VatControllerBlockEntity vat = vat(ctx.getSource().getLevel());
        CompoundTag saved = new CompoundTag();
        saved.put("Gas", vat.getGasTankContents().isEmpty() ? new CompoundTag()
            : vat.getGasTankContents().save(ctx.getSource().getLevel().registryAccess()));
        saved.putInt("GasAmount", vat.getGasTankContents().getAmount());
        saved.put("Liquid", vat.getLiquidTankContents().isEmpty() ? new CompoundTag()
            : vat.getLiquidTankContents().save(ctx.getSource().getLevel().registryAccess()));
        saved.putInt("LiquidAmount", vat.getLiquidTankContents().getAmount());
        saved.put("Catalyst", vat.inventory.getStackInSlot(0).isEmpty() ? new CompoundTag()
            : vat.inventory.getStackInSlot(0).save(ctx.getSource().getLevel().registryAccess()));
        source(ctx.getSource().getLevel()).savedM8 = saved;
        source(ctx.getSource().getLevel()).setChanged();
        check(ctx, !saved.getCompound("Gas").isEmpty() || !saved.getCompound("Liquid").isEmpty(),
            "контрольная Destroy-смесь сохранена перед перезапуском");
        return report(ctx);
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
        vat.getLiquidTank().setFluid(mixture(molecules));
    }

    private static FluidStack mixture(Map<LegacySpecies, Double> molecules) {
        LegacyMixture mixture = new LegacyMixture();
        molecules.forEach((molecule, concentration) -> mixture.addMolecule(molecule, concentration.floatValue()));
        mixture.heat((298.15F - mixture.getTemperature()) * mixture.getVolumetricHeatCapacity());
        return MixtureFluid.of(1000, mixture);
    }

    private static void catalyst(VatControllerBlockEntity vat, Item item) {
        vat.inventory.insertItem(0, new ItemStack(item), false);
    }

    private static boolean catalystKept(VatControllerBlockEntity vat, Item item) {
        return vat.inventory.getStackInSlot(0).is(item) && vat.inventory.getStackInSlot(0).getCount() == 1;
    }

    private static double molesBoth(FluidStack gas, FluidStack liquid, LegacySpecies molecule) {
        return moles(gas, molecule) + moles(liquid, molecule);
    }

    private static double moles(FluidStack stack, LegacySpecies molecule) {
        return stack.isEmpty() ? 0 : mixture(stack).getConcentrationOf(molecule) * stack.getAmount();
    }

    private static LegacyMixture mixture(FluidStack stack) {
        return LegacyMixture.readNBT(stack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
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
        throw new IllegalStateException("На позиции стенда нет штатного Vat M8");
    }

    private static TestSourceBlockEntity source(ServerLevel level) {
        if (level.getBlockEntity(SOURCE) instanceof TestSourceBlockEntity source) return source;
        throw new IllegalStateException("На позиции стенда нет сохраняемого состояния M8");
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
