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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlock;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.bridge.fluid.HydrochloricAcidAdapter;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ElectrolyzerStatus;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlockEntity;
import tfc.destroy.extras.recipe.MembraneElectrolysisRecipe;

/** Проверки production-машины M2; в release jar этот мод не входит. */
@Mod(DestroyExtrasTest.MOD_ID)
public final class DestroyExtrasTest {
    public static final String MOD_ID = DestroyExtras.MOD_ID + "_devtest";
    private static final BlockPos PROCESS = new BlockPos(0, -60, 10);
    private static final BlockPos SOURCE = PROCESS.west();
    private static final BlockPos BATTERY = PROCESS.east(2);
    private static final BlockPos VAT = new BlockPos(10, -60, 11);
    private static final ResourceLocation SALT_WATER = DestroyExtras.rl("electrolysis/salt_water");

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredBlock<TestSourceBlock> SOURCE_BLOCK = BLOCKS.register("source",
        () -> new TestSourceBlock(Block.Properties.of()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestSourceBlockEntity>> SOURCE_BE =
        BLOCK_ENTITIES.register("source", () -> BlockEntityType.Builder
            .of(TestSourceBlockEntity::new, SOURCE_BLOCK.get()).build(null));

    private static int checks;
    private static int failures;
    private static boolean vatThermostat;

    public DestroyExtrasTest(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        M4Test.register(modBus);
        modBus.addListener(DestroyExtrasTest::capabilities);
        NeoForge.EVENT_BUS.addListener(DestroyExtrasTest::commands);
        NeoForge.EVENT_BUS.addListener(M4Test::commands);
        NeoForge.EVENT_BUS.addListener(DestroyExtrasTest::serverTick);
    }

    private static void serverTick(ServerTickEvent.Post event) {
        if (vatThermostat && event.getServer().overworld().getBlockEntity(VAT) instanceof VatControllerBlockEntity vat) {
            vat.changeHeatingPower(vat.getTemperature() > 298.15F ? -30_000_000.0F : 0.0F);
        }
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SOURCE_BE.get(), (be, side) -> be.tank);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
            .then(Commands.literal("environment").executes(ctx -> {
                reset();
                var holder = ctx.getSource().getLevel().getRecipeManager().byKey(SALT_WATER).orElse(null);
                check(ctx, holder != null && holder.value() instanceof MembraneElectrolysisRecipe,
                    "JSON-рецепт destroy_extras:electrolysis/salt_water загружен");
                if (holder != null && holder.value() instanceof MembraneElectrolysisRecipe recipe) {
                    check(ctx, recipe.input().amount() == 1000 && recipe.getProcessingDuration() == 200,
                        "партия 1000 мБ и длительность 200 тиков заданы JSON");
                    check(ctx, recipe.saltConcentration() == 1 && recipe.waterConcentration() == 10,
                        "концентрации соли и воды заданы JSON");
                    check(ctx, recipe.products().length == 3, "тип принимает три независимых жидких выхода");
                }
                return report(ctx);
            }))
            .then(Commands.literal("power").then(Commands.argument("volts", DoubleArgumentType.doubleArg(0))
                .executes(ctx -> power(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "volts")))))
            .then(Commands.literal("fill").then(Commands.argument("output", StringArgumentType.word())
                .executes(ctx -> fill(ctx, StringArgumentType.getString(ctx, "output")))))
            .then(Commands.literal("membrane")
                .then(Commands.literal("remove").executes(ctx -> {
                    MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
                    process.membrane.setStackInSlot(0, ItemStack.EMPTY);
                    process.setChanged();
                    ctx.getSource().sendSuccess(() -> Component.literal("мембрана удалена"), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.argument("damage", IntegerArgumentType.integer(0, 64))
                    .executes(ctx -> membrane(ctx, IntegerArgumentType.getInteger(ctx, "damage")))))
            .then(Commands.literal("report").executes(ctx -> reportState(ctx.getSource())))
            .then(Commands.literal("m3build").executes(ctx -> m3build(ctx.getSource())))
            .then(Commands.literal("m3prepare").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> m3prepare(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m3assert").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> m3assert(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("restartcase").executes(DestroyExtrasTest::restartCase))
            .then(Commands.literal("assert").executes(DestroyExtrasTest::report)
                .then(Commands.argument("case", StringArgumentType.word())
                    .executes(ctx -> assertCase(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("fail").executes(ctx -> {
                reset();
                check(ctx, false, "намеренно неверное ожидание M2");
                return report(ctx);
            })));
    }

    private static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        place(level, SOURCE, SOURCE_BLOCK.get().defaultBlockState());
        place(level, PROCESS, DestroyExtras.ELECTROLYZER_BLOCK.get().defaultBlockState());
        place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        source(level).tank.setFluid(new FluidStack(TFCFluids.SALT_WATER.getSource(), 8000));
        process(level).membrane.setStackInSlot(0, new ItemStack(DestroyExtras.MEMBRANE_ITEM.get()));
        wire(level, BATTERY, 0, PROCESS, ProcessPowerDevice.PLUS);
        wire(level, BATTERY, 1, PROCESS, ProcessPowerDevice.MINUS);
        source.sendSuccess(() -> Component.literal("стенд production M2 собран"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void wire(ServerLevel level, BlockPos a, int nodeA, BlockPos b, int nodeB) {
        for (BlockPos pos : new BlockPos[] {a, b}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ElectricalDeviceBlock<?> block) block.ensureNodesExist(level, pos, state);
        }
        InfrastructureSavedData.load(level).connect(new InWorldNode(nodeA, a), new InWorldNode(nodeB, b),
            CEEWireTypes.COPPER.get());
    }

    private static int power(CommandSourceStack source, double volts) {
        CreativeBatteryDevice battery = DevicesSavedData.load(source.getLevel())
            .getDevice(BATTERY, CreativeBatteryDevice.class);
        if (battery == null) {
            source.sendFailure(Component.literal("нет EE-батареи стенда M2"));
            return 0;
        }
        MembraneElectrolyzerBlockEntity process = process(source.getLevel());
        if (process.progress > 0 && volts < 270) {
            TestSourceBlockEntity sourceBlock = source(source.getLevel());
            sourceBlock.savedProgress = process.progress;
            sourceBlock.setChanged();
        }
        battery.voltage = volts;
        battery.acFrequency = 0;
        DevicesSavedData.load(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("напряжение стенда M2: " + volts + " В"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int fill(CommandContext<CommandSourceStack> ctx, String output) {
        MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
        int index = outputIndex(output);
        if (index < 0) {
            ctx.getSource().sendFailure(Component.literal("неизвестный выход: " + output));
            return 0;
        }
        FluidStack[] products = recipe(ctx.getSource().getLevel()).products();
        process.outputs[index].setFluid(products[index].copyWithAmount(MembraneElectrolyzerBlockEntity.OUTPUT_CAPACITY));
        process.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("заполнен выход " + output), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int membrane(CommandContext<CommandSourceStack> ctx, int damage) {
        MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
        ItemStack stack = process.membrane.getStackInSlot(0).copy();
        if (stack.isEmpty()) stack = new ItemStack(DestroyExtras.MEMBRANE_ITEM.get());
        stack.setDamageValue(damage);
        process.membrane.setStackInSlot(0, stack);
        ctx.getSource().sendSuccess(() -> Component.literal("износ мембраны: " + damage), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int restartCase(CommandContext<CommandSourceStack> ctx) {
        reset();
        MembraneElectrolyzerBlockEntity process = process(ctx.getSource().getLevel());
        check(ctx, process.progress > 0 && process.progress < recipe(ctx.getSource().getLevel()).getProcessingDuration(),
            "перед перезапуском есть незавершённый прогресс");
        TestSourceBlockEntity source = source(ctx.getSource().getLevel());
        source.savedProgress = process.progress;
        source.savedBatches = process.batches;
        source.savedMembraneDamage = process.membrane.getStackInSlot(0).getDamageValue();
        source.savedMixture = process.mixture.getFluid().copy();
        source.setChanged();
        power(ctx.getSource(), 0);
        return report(ctx);
    }

    private static int assertCase(CommandContext<CommandSourceStack> ctx, String testCase) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        MembraneElectrolyzerBlockEntity process = process(level);
        ProcessPowerDevice device = process.device();
        FluidStack[] products = recipe(level).products();
        switch (testCase) {
            case "ports" -> {
                check(ctx, capability(level, Direction.NORTH) == process.hydrogen, "север — водород");
                check(ctx, capability(level, Direction.SOUTH) == process.chlorine, "юг — хлор");
                check(ctx, capability(level, Direction.EAST) == process.sodiumHydroxide, "восток — NaOH");
                check(ctx, capability(level, Direction.WEST) == process.input, "запад — вход TFC");
                check(ctx, level.getCapability(Capabilities.ItemHandler.BLOCK, PROCESS, Direction.UP) == process.membrane,
                    "item handler открывает заменяемый слот мембраны");
            }
            case "normal" -> {
                checkPower(ctx, device, 300, 8);
                check(ctx, process.batches == 1, "выполнена ровно одна production-партия");
                checkProducts(ctx, process, products);
                check(ctx, process.remainingMembraneOperations() == 63, "одна операция износила мембрану на 1/64");
                check(ctx, remainingWater(level) == 7000, "8000 - 1000 = 7000 мБ исходной массы");
                check(ctx, SALT_WATER.equals(process.recipeId()), "production-блок использует JSON-рецепт");
            }
            case "no_membrane", "worn_membrane" -> {
                check(ctx, process.status() == ElectrolyzerStatus.FAULT, "отсутствующая или изношенная мембрана даёт FAULT");
                check(ctx, process.progress == 0 && process.batches == 0 && emptyOutputs(process),
                    "без исправной мембраны нет прогресса и продуктов");
                check(ctx, remainingWater(level) == 8000, "вся исходная масса сохранена");
            }
            case "undervoltage" -> {
                checkPower(ctx, device, 200, 200.0 / ProcessPowerDevice.LOAD_RESISTANCE);
                check(ctx, device != null && !device.hasWorkingPower(), "200 В не входит в рабочую зону");
                int saved = source(level).savedProgress;
                check(ctx, saved > 0 && process.progress >= saved && process.progress <= saved + 1 && process.batches == 0,
                    "незавершённый прогресс сохранён, после просадки не более одного переходного тика");
                check(ctx, emptyOutputs(process) && remainingWater(level) == 8000,
                    "просадка не создаёт продукты и не теряет исходную массу");
            }
            case "blocked_hydrogen", "blocked_chlorine", "blocked_sodium_hydroxide" -> {
                int blocked = switch (testCase) {
                    case "blocked_hydrogen" -> 0;
                    case "blocked_chlorine" -> 1;
                    default -> 2;
                };
                check(ctx, process.status() == ElectrolyzerStatus.BLOCKED_OUTPUT, "статус BLOCKED_OUTPUT");
                check(ctx, process.outputs[blocked].getFluidAmount() == MembraneElectrolyzerBlockEntity.OUTPUT_CAPACITY,
                    "выбранный выход действительно полон");
                for (int i = 0; i < 3; i++) {
                    if (i != blocked) check(ctx, process.outputs[i].isEmpty(), "другой выход " + i + " остался пустым");
                }
                check(ctx, process.progress == 0 && process.batches == 0, "переполнение блокирует всю операцию");
                check(ctx, remainingWater(level) == 8000, "масса входа сохранена при переполнении");
            }
            case "persisted" -> {
                TestSourceBlockEntity source = source(level);
                check(ctx, source.savedProgress > 0 && process.progress == source.savedProgress,
                    "прогресс восстановлен без изменения");
                check(ctx, process.batches == source.savedBatches && emptyOutputs(process),
                    "партия и продукты не повторились после reload");
                check(ctx, process.membrane.getStackInSlot(0).getDamageValue() == source.savedMembraneDamage,
                    "износ мембраны сохранён");
                check(ctx, FluidStack.isSameFluidSameComponents(process.mixture.getFluid(), source.savedMixture),
                    "точный NBT Destroy-смеси восстановлен");
                check(ctx, SALT_WATER.equals(process.recipeId()), "id рецепта восстановлен");
                check(ctx, remainingWater(level) == 8000, "все баки сохранили исходную массу");
            }
            default -> check(ctx, false, "неизвестный сценарий " + testCase);
        }
        return report(ctx);
    }

    private static int m3build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(VAT.offset(-2, -2, -4), VAT.offset(2, 2, 0))) {
            boolean inside = Math.abs(pos.getX() - VAT.getX()) <= 1 && Math.abs(pos.getY() - VAT.getY()) <= 1
                && pos.getZ() >= VAT.getZ() - 3 && pos.getZ() < VAT.getZ();
            place(level, pos, inside ? Blocks.AIR.defaultBlockState() : Blocks.NETHERITE_BLOCK.defaultBlockState());
        }
        place(level, VAT, DestroyBlocks.VAT_CONTROLLER.getDefaultState().setValue(VatControllerBlock.FACING, Direction.SOUTH));
        VatControllerBlockEntity vat = vat(level);
        if (vat.getVatOptional().isEmpty()) vat.tryMakeVat();
        vatThermostat = false;
        reset();
        check(source, vat.getVatOptional().isPresent(), "штатный Vat 3×3×3 сформирован из незеритовых блоков");
        check(source, vat.getVatOptional().map(v -> v.getCapacity()).orElse(0) == 27000,
            "ёмкость штатного Vat — 27000 мБ");
        check(source, petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.REACTIONS
            .containsKey("destroy:hydrogen_chloride_synthesis"), "загружена штатная реакция H₂ + Cl₂ → 2 HCl");
        check(source, petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.REACTIONS
            .containsKey("destroy:iron_dissolution"), "загружена штатная реакция растворения железа до Fe(III)");
        return report(source);
    }

    private static int m3prepare(CommandContext<CommandSourceStack> ctx, String testCase) {
        VatControllerBlockEntity vat = vat(ctx.getSource().getLevel());
        vatThermostat = testCase.equals("hcl");
        switch (testCase) {
            case "hcl", "hcl_no_uv" -> {
                FluidStack[] products = recipe(ctx.getSource().getLevel()).products();
                LegacyMixture hydrogen = mixture(products[0]);
                LegacyMixture chlorine = mixture(products[1]);
                LegacyMixture feed = LegacyMixture.mix(Map.of(
                    hydrogen, 5400.0, chlorine, 5400.0, new LegacyMixture(), 16200.0));
                feed.heat((298.15F - feed.getTemperature()) * feed.getVolumetricHeatCapacity());
                vat.getGasTank().setFluid(MixtureFluid.of(27000, feed));
                if (testCase.equals("hcl")) vat.changeUVPower(1.0F);
            }
            case "absorption" -> {
                FluidStack absorbed = HydrochloricAcidAdapter.absorb(
                    pure(DestroyMolecules.HYDROCHLORIC_ACID, 25), pure(DestroyMolecules.WATER, 250));
                if (absorbed.isEmpty()) {
                    ctx.getSource().sendFailure(Component.literal("атомарное поглощение HCl отклонено"));
                    return 0;
                }
            }
            case "iron" -> {
                LegacyMixture acid = new LegacyMixture()
                    .addMolecule(DestroyMolecules.PROTON, 50.0F / 550)
                    .addMolecule(DestroyMolecules.CHLORIDE, 50.0F / 550)
                    .addMolecule(DestroyMolecules.WATER, 500.0F / 550);
                acid.heat((298.15F - acid.getTemperature()) * acid.getVolumetricHeatCapacity());
                vat.getLiquidTank().setFluid(MixtureFluid.of(550, acid));
                if (!vat.inventory.insertItem(0, new ItemStack(DestroyItems.IRON_POWDER.get()), false).isEmpty()) {
                    ctx.getSource().sendFailure(Component.literal("железная пыль не принята инвентарём Vat"));
                    return 0;
                }
            }
            default -> {
                ctx.getSource().sendFailure(Component.literal("неизвестный сценарий M3: " + testCase));
                return 0;
            }
        }
        vat.updateCachedMixture();
        vat.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("подготовлен сценарий M3 " + testCase), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int m3assert(CommandContext<CommandSourceStack> ctx, String testCase) {
        reset();
        VatControllerBlockEntity vat = vat(ctx.getSource().getLevel());
        FluidStack gas = vat.getGasTankContents();
        FluidStack liquid = vat.getLiquidTankContents();
        if (testCase.equals("absorption")) {
            gas = FluidStack.EMPTY;
            liquid = HydrochloricAcidAdapter.absorb(
                pure(DestroyMolecules.HYDROCHLORIC_ACID, 25), pure(DestroyMolecules.WATER, 250));
        }
        switch (testCase) {
            case "hcl" -> {
                double acid = moles(gas, DestroyMolecules.HYDROCHLORIC_ACID);
                check(ctx, acid > 1.0, "UV запускает синтез HCl в штатном Vat");
                check(ctx, close(5400 - moles(gas, DestroyMolecules.HYDROGEN), acid / 2, 1.0)
                    && close(5400 - moles(gas, DestroyMolecules.CHLORINE), acid / 2, 1.0),
                    "на 2 HCl расходуются ровно 1 H₂ и 1 Cl₂");
                check(ctx, close(moles(gas, DestroyMolecules.HYDROGEN), moles(gas, DestroyMolecules.CHLORINE), 0.01),
                    "остатки H₂ и Cl₂ сохраняют исходное соотношение 1:1");
                check(ctx, gas.getAmount() > 0 && liquid.isEmpty(), "HCl остаётся в штатном газовом выходе");
            }
            case "hcl_no_uv" -> {
                check(ctx, close(moles(gas, DestroyMolecules.HYDROGEN), 5400, 0.01)
                    && close(moles(gas, DestroyMolecules.CHLORINE), 5400, 0.01),
                    "без UV исходные H₂ и Cl₂ сохранены");
                check(ctx, moles(gas, DestroyMolecules.HYDROCHLORIC_ACID) < 0.001,
                    "без UV синтез HCl не идёт");
            }
            case "absorption" -> {
                double initial = 25 * DestroyMolecules.HYDROCHLORIC_ACID.getPureConcentration();
                double chlorine = moles(gas, DestroyMolecules.HYDROCHLORIC_ACID)
                    + moles(liquid, DestroyMolecules.HYDROCHLORIC_ACID) + moles(liquid, DestroyMolecules.CHLORIDE);
                check(ctx, close(moles(liquid, DestroyMolecules.PROTON), initial, 0.05)
                    && close(moles(liquid, DestroyMolecules.CHLORIDE), initial, 0.05),
                    "атомарное поглощение даёт равные H⁺ и Cl⁻ по одному на HCl");
                check(ctx, close(chlorine, initial, 0.05), "хлор сохраняется при поглощении HCl водой");
                check(ctx, close(moles(liquid, DestroyMolecules.WATER),
                    250 * DestroyMolecules.WATER.getPureConcentration(), 0.05)
                    && liquid.getAmount() == 275, "вода и объём жидкой фазы сохранены");
            }
            case "iron" -> {
                check(ctx, vat.inventory.getStackInSlot(0).isEmpty(), "одна железная пыль израсходована");
                check(ctx, close(moles(liquid, DestroyMolecules.IRON_III), 9.0, 0.3),
                    "одна пыль даёт 9 молей Fe(III) с численной погрешностью Destroy до 3 %");
                check(ctx, close(moles(gas, DestroyMolecules.HYDROGEN), 13.5, 0.1),
                    "побочный H₂ выдан в газовую фазу: 13,5 моля");
                check(ctx, moles(liquid, DestroyMolecules.IRON_II) < 0.001,
                    "обход через отдельный FeCl₂ не требуется");
                double acidHydrogenLeft = moles(gas, DestroyMolecules.HYDROCHLORIC_ACID)
                    + moles(liquid, DestroyMolecules.HYDROCHLORIC_ACID) + moles(liquid, DestroyMolecules.PROTON);
                check(ctx, close(50 - acidHydrogenLeft, 27.0, 0.9),
                    "на пыль израсходовано 27 H⁺ с численной погрешностью Destroy до 3 %");
            }
            default -> check(ctx, false, "неизвестный сценарий M3 " + testCase);
        }
        ctx.getSource().sendSystemMessage(Component.literal("M3 gas=" + gas.getAmount() + " мБ, liquid="
            + liquid.getAmount() + " мБ, T=" + (gas.isEmpty() ? 0 : mixture(gas).getTemperature())
            + ", H2=" + moles(gas, DestroyMolecules.HYDROGEN) + ", Cl2=" + moles(gas, DestroyMolecules.CHLORINE)
            + ", HCl(g/l)=" + moles(gas, DestroyMolecules.HYDROCHLORIC_ACID) + "/"
            + moles(liquid, DestroyMolecules.HYDROCHLORIC_ACID)
            + ", H+=" + moles(liquid, DestroyMolecules.PROTON)
            + ", Cl-=" + moles(liquid, DestroyMolecules.CHLORIDE)
            + ", Fe(III)=" + moles(liquid, DestroyMolecules.IRON_III)
            + ", gasContents=" + contents(gas) + ", liquidContents=" + contents(liquid)));
        return report(ctx);
    }

    private static String contents(FluidStack stack) {
        if (stack.isEmpty()) return "{}";
        LegacyMixture mixture = mixture(stack);
        StringBuilder result = new StringBuilder("{");
        for (LegacySpecies molecule : mixture.getContents(false)) {
            if (result.length() > 1) result.append(", ");
            result.append(molecule.getFullID()).append('=').append(moles(stack, molecule));
        }
        return result.append('}').toString();
    }

    private static FluidStack pure(LegacySpecies molecule, int amount) {
        LegacyMixture mixture = new LegacyMixture().addMolecule(molecule, molecule.getPureConcentration());
        mixture.heat((298.15F - mixture.getTemperature()) * mixture.getVolumetricHeatCapacity());
        return MixtureFluid.of(amount, mixture);
    }

    private static LegacyMixture mixture(FluidStack stack) {
        return LegacyMixture.readNBT(stack.getOrDefault(DestroyDataComponents.MIXTURE, new net.minecraft.nbt.CompoundTag()));
    }

    private static double moles(FluidStack stack, LegacySpecies molecule) {
        return stack.isEmpty() ? 0 : mixture(stack).getConcentrationOf(molecule) * stack.getAmount();
    }

    private static VatControllerBlockEntity vat(ServerLevel level) {
        if (level.getBlockEntity(VAT) instanceof VatControllerBlockEntity vat) return vat;
        throw new IllegalStateException("На позиции стенда нет штатного Vat");
    }

    private static void checkPower(CommandContext<CommandSourceStack> ctx, ProcessPowerDevice device,
        double volts, double amps) {
        check(ctx, device != null, "EE-драйвер production-блока создан");
        if (device != null) {
            check(ctx, close(device.measuredVoltage(), volts, 0.25), "измерено около " + volts + " В");
            check(ctx, close(device.measuredCurrent(), amps, 0.1), "измерен ожидаемый ток");
        }
    }

    private static void checkProducts(CommandContext<CommandSourceStack> ctx, MembraneElectrolyzerBlockEntity process,
        FluidStack[] products) {
        for (int i = 0; i < 3; i++) {
            check(ctx, process.outputs[i].getFluidAmount() == products[i].getAmount(), "точное количество выхода " + i);
            check(ctx, FluidStack.isSameFluidSameComponents(process.outputs[i].getFluid(), products[i]),
                "fluid id и data components выхода " + i + " совпадают с JSON");
        }
    }

    private static IFluidHandler capability(ServerLevel level, Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, PROCESS, side);
    }

    private static int outputIndex(String output) {
        return switch (output) {
            case "hydrogen" -> 0;
            case "chlorine" -> 1;
            case "sodium_hydroxide" -> 2;
            default -> -1;
        };
    }

    private static MembraneElectrolysisRecipe recipe(ServerLevel level) {
        if (level.getRecipeManager().byKey(SALT_WATER).orElse(null) instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
            && holder.value() instanceof MembraneElectrolysisRecipe recipe) return recipe;
        throw new IllegalStateException("JSON-рецепт M2 не загружен");
    }

    private static boolean emptyOutputs(MembraneElectrolyzerBlockEntity process) {
        for (var output : process.outputs) if (!output.isEmpty()) return false;
        return true;
    }

    private static int remainingWater(ServerLevel level) {
        MembraneElectrolyzerBlockEntity process = process(level);
        return source(level).tank.getFluidAmount() + process.input.getFluidAmount() + process.mixture.getFluidAmount();
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static MembraneElectrolyzerBlockEntity process(ServerLevel level) {
        if (level.getBlockEntity(PROCESS) instanceof MembraneElectrolyzerBlockEntity process) return process;
        throw new IllegalStateException("На позиции стенда нет production-электролизёра");
    }

    private static TestSourceBlockEntity source(ServerLevel level) {
        if (level.getBlockEntity(SOURCE) instanceof TestSourceBlockEntity source) return source;
        throw new IllegalStateException("На позиции стенда нет исходного бака");
    }

    private static int reportState(CommandSourceStack source) {
        MembraneElectrolyzerBlockEntity process = process(source.getLevel());
        ProcessPowerDevice device = process.device();
        String message = "M2 " + process.status() + " progress=" + process.progress + " batches=" + process.batches
            + " membrane=" + process.remainingMembraneOperations() + " tanks=" + process.input.getFluidAmount() + "/"
            + process.mixture.getFluidAmount() + "/" + process.hydrogen.getFluidAmount() + "/"
            + process.chlorine.getFluidAmount() + "/" + process.sodiumHydroxide.getFluidAmount();
        if (device != null) message += String.format(" V=%.3f A=%.3f P=%.1f", device.measuredVoltage(),
            device.measuredCurrent(), device.measuredPower());
        String result = message;
        source.sendSuccess(() -> Component.literal(result), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void reset() {
        checks = 0;
        failures = 0;
    }

    private static void check(CommandContext<CommandSourceStack> ctx, boolean condition, String label) {
        check(ctx.getSource(), condition, label);
    }

    private static void check(CommandSourceStack source, boolean condition, String label) {
        checks++;
        if (!condition) failures++;
        source.sendSystemMessage(Component.literal("DESTROYEXTRATEST " + (condition ? "OK " : "FAIL ") + label));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        return report(ctx.getSource());
    }

    private static int report(CommandSourceStack source) {
        int passed = checks - failures;
        source.sendSystemMessage(Component.literal(
            "DESTROYEXTRATEST " + (failures == 0 && checks > 0 ? "PASS " : "FAIL ") + passed + "/" + checks));
        return failures == 0 && checks > 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
