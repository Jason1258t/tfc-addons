package tfc.destroy.extras.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import ee.extras.pcb.PcbItems;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.registries.DeferredItem;
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
import tfc.destroy.bridge.fluid.HydrochloricAcidAdapter;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;
import tfc.destroy.extras.etching.EtchingChemistry;
import tfc.destroy.extras.etching.EtchingStatus;
import tfc.destroy.extras.recipe.EtchingRecipe;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;

/** Сквозные проверки M4; тестовые платы и рецепт остаются только в devtest. */
final class M4Test {
    private static final BlockPos BATH = new BlockPos(0, -60, 20);
    private static final BlockPos SOURCE = BATH.west();
    private static final BlockPos VAT = new BlockPos(10, -60, 21);
    private static final ResourceLocation L1_RECIPE = DestroyExtras.rl("etching/l1_board");
    private static final ResourceLocation DEVTEST_RECIPE = DestroyExtras.rl("etching/devtest_board");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DestroyExtrasTest.MOD_ID);

    private static final DeferredItem<Item> BOARD_BLANK = ITEMS.register("board_blank",
        () -> new Item(new Item.Properties()));
    private static final DeferredItem<Item> BOARD_MASK = ITEMS.register("board_mask",
        () -> new Item(new Item.Properties()));
    private static final DeferredItem<Item> ETCHED_BOARD = ITEMS.register("etched_board",
        () -> new Item(new Item.Properties()));

    private static int checks;
    private static int failures;

    private M4Test() {}

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m4environment").executes(M4Test::environment))
            .then(Commands.literal("m4build").executes(ctx -> build(ctx.getSource())))
            .then(Commands.literal("m4acid").executes(ctx -> acid(ctx.getSource())))
            .then(Commands.literal("m4load").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m4assert").then(Commands.argument("case", StringArgumentType.word())
                .executes(ctx -> assertCase(ctx, StringArgumentType.getString(ctx, "case")))))
            .then(Commands.literal("m4restartcase").executes(M4Test::restartCase))
            .then(Commands.literal("m4resume").executes(ctx -> {
                bath(ctx.getSource().getLevel()).spent.setFluid(FluidStack.EMPTY);
                ctx.getSource().sendSuccess(() -> Component.literal("выход ванны M4 освобождён"), false);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("m4break").executes(M4Test::breakCase))
            .then(Commands.literal("m4report").executes(ctx -> reportState(ctx.getSource()))));
    }

    private static int environment(CommandContext<CommandSourceStack> ctx) {
        reset();
        for (ResourceLocation id : new ResourceLocation[] {L1_RECIPE, DEVTEST_RECIPE}) {
            EtchingRecipe recipe = recipe(ctx.getSource().getLevel(), id);
            check(ctx, recipe.input().amount() == 250 && recipe.getProcessingDuration() == 400,
                id + ": 250 мБ и 400 тиков заданы JSON");
            check(ctx, recipe.copperMoles() == 3.0F && recipe.getFluidResults().size() == 1,
                id + ": медь и показательный spent etchant заданы JSON");
        }
        check(ctx, PcbItems.L1_BOARD_BLANK.get().builtInRegistryHolder().isBound()
            && PcbItems.L1_MASK.get().builtInRegistryHolder().isBound()
            && PcbItems.L1_BOARD.get().builtInRegistryHolder().isBound(), "настоящие предметы L1 зарегистрированы в EE Extras");
        check(ctx, !ModList.get().isLoaded("computercraft") && !ModList.get().isLoaded("cc_tweaked"),
            "стенд M4 не использует CC");
        return report(ctx);
    }

    private static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        place(level, SOURCE, DestroyExtrasTest.SOURCE_BLOCK.get().defaultBlockState());
        place(level, BATH, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState());
        for (BlockPos pos : BlockPos.betweenClosed(VAT.offset(-2, -2, -4), VAT.offset(2, 2, 0))) {
            boolean inside = Math.abs(pos.getX() - VAT.getX()) <= 1 && Math.abs(pos.getY() - VAT.getY()) <= 1
                && pos.getZ() >= VAT.getZ() - 3 && pos.getZ() < VAT.getZ();
            place(level, pos, inside ? Blocks.AIR.defaultBlockState() : Blocks.NETHERITE_BLOCK.defaultBlockState());
        }
        place(level, VAT, DestroyBlocks.VAT_CONTROLLER.getDefaultState().setValue(VatControllerBlock.FACING, Direction.SOUTH));
        VatControllerBlockEntity vat = vat(level);
        if (vat.getVatOptional().isEmpty()) vat.tryMakeVat();
        source.sendSuccess(() -> Component.literal("стенд M4: Bridge absorption → Destroy Vat → etching bath"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int acid(CommandSourceStack source) {
        VatControllerBlockEntity vat = vat(source.getLevel());
        FluidStack absorbed = HydrochloricAcidAdapter.absorb(
            pure(DestroyMolecules.HYDROCHLORIC_ACID, 25), pure(DestroyMolecules.WATER, 250));
        if (absorbed.isEmpty()) throw new IllegalStateException("Bridge не выполнил поглощение HCl для M4");
        vat.getLiquidTank().setFluid(absorbed);
        if (!vat.inventory.insertItem(0, new ItemStack(DestroyItems.IRON_POWDER.get()), false).isEmpty()) {
            throw new IllegalStateException("Vat не принял железную пыль для M4");
        }
        vat.updateCachedMixture();
        vat.setChanged();
        source.sendSuccess(() -> Component.literal("HCl поглощён атомарной Bridge-операцией, железо загружено"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int load(CommandContext<CommandSourceStack> ctx, String testCase) {
        ServerLevel level = ctx.getSource().getLevel();
        EtchingBathBlockEntity bath = bath(level);
        FluidStack etchant;
        if (testCase.equals("insufficient")) {
            etchant = pure(DestroyMolecules.WATER, 250);
        } else {
            etchant = vat(level).getLiquidTank().drain(250, FluidAction.EXECUTE);
            if (etchant.getAmount() != 250) throw new IllegalStateException("Не хватило FeCl₃ из штатного Vat");
        }
        bath.input.setFluid(etchant);
        bath.spent.setFluid(FluidStack.EMPTY);
        ItemStack blank;
        ItemStack mask;
        if (testCase.equals("devtest")) {
            blank = new ItemStack(BOARD_BLANK.get());
            mask = new ItemStack(BOARD_MASK.get());
            CompoundTag data = new CompoundTag();
            data.put("topology", topology());
            mask.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        } else {
            blank = new ItemStack(PcbItems.L1_BOARD_BLANK.get());
            mask = new ItemStack(PcbItems.L1_MASK.get());
            mask.set(PcbItems.PCB_TOPOLOGY.get(), topology());
        }
        bath.items.setStackInSlot(0, blank);
        bath.items.setStackInSlot(1, mask);
        if (testCase.equals("blocked")) bath.spent.setFluid(pure(DestroyMolecules.WATER, EtchingBathBlockEntity.SPENT_CAPACITY));

        CompoundTag saved = new CompoundTag();
        saved.putDouble("InitialFeIII", EtchingChemistry.moles(etchant, DestroyMolecules.IRON_III));
        saved.putDouble("InitialChloride", EtchingChemistry.moles(etchant, DestroyMolecules.CHLORIDE));
        saved.putDouble("InitialWater", EtchingChemistry.moles(etchant, DestroyMolecules.WATER));
        saved.putFloat("InitialTemperature", mixture(etchant).getTemperature());
        saved.put("Topology", topology(mask));
        source(level).savedM4 = saved;
        source(level).setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("ванна M4 загружена: " + testCase
            + ", Fe(III)=" + saved.getDouble("InitialFeIII")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assertCase(CommandContext<CommandSourceStack> ctx, String testCase) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        EtchingBathBlockEntity bath = bath(level);
        CompoundTag saved = source(level).savedM4;
        switch (testCase) {
            case "l1", "devtest" -> {
                ItemStack expectedBoard = new ItemStack(testCase.equals("l1") ? PcbItems.L1_BOARD.get() : ETCHED_BOARD.get());
                check(ctx, bath.batches == 1 && bath.progress == 0, "выполнена ровно одна операция на 400 тиков");
                check(ctx, bath.items.getStackInSlot(0).isEmpty() && bath.items.getStackInSlot(1).isEmpty(),
                    "заготовка и маска израсходованы по одной");
                check(ctx, ItemStack.isSameItem(bath.items.getStackInSlot(2), expectedBoard)
                    && bath.items.getStackInSlot(2).getCount() == 1, "выдана правильная плата");
                check(ctx, topology(bath.items.getStackInSlot(2)).equals(saved.getCompound("Topology")),
                    "топология результата точно совпадает с маской");
                check(ctx, bath.input.isEmpty() && bath.spent.getFluidAmount() == 250,
                    "250 мБ FeCl₃ превращены в 250 мБ отработанного раствора");
                check(ctx, close(EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.COPPER_II), 3, 0.001)
                    && close(EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.IRON_II), 6, 0.001),
                    "образованы 3 Cu(II) и 6 Fe(II)");
                check(ctx, close(EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.IRON_III),
                    saved.getDouble("InitialFeIII") - 6, 0.01), "израсходованы ровно 6 Fe(III)");
                check(ctx, close(EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.CHLORIDE),
                    saved.getDouble("InitialChloride"), 0.01)
                    && close(EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.WATER),
                    saved.getDouble("InitialWater"), 0.01), "хлорид, вода и остальные компоненты сохранены");
                check(ctx, close(mixture(bath.spent.getFluid()).getTemperature(), saved.getFloat("InitialTemperature"), 0.001),
                    "температура смеси сохранена");
                check(ctx, (testCase.equals("l1") ? L1_RECIPE : DEVTEST_RECIPE).equals(bath.recipeId()),
                    "ванна использует выбранный JSON-рецепт");
            }
            case "blocked" -> {
                check(ctx, bath.status() == EtchingStatus.BLOCKED_OUTPUT, "полный выход даёт BLOCKED_OUTPUT");
                check(ctx, bath.progress == 0 && bath.batches == 0, "переполнение не запускает операцию");
                check(ctx, bath.input.getFluidAmount() == 250 && !bath.items.getStackInSlot(0).isEmpty()
                    && !bath.items.getStackInSlot(1).isEmpty() && bath.items.getStackInSlot(2).isEmpty(),
                    "жидкость и оба предмета сохранены, плата не создана");
            }
            case "insufficient" -> {
                check(ctx, bath.status() == EtchingStatus.IDLE && bath.progress == 0 && bath.batches == 0,
                    "смесь без Fe(III) не запускает травление");
                check(ctx, bath.input.getFluidAmount() == 250 && bath.spent.isEmpty()
                    && !bath.items.getStackInSlot(0).isEmpty() && !bath.items.getStackInSlot(1).isEmpty(),
                    "неподходящий раствор и предметы не расходуются");
            }
            case "persisted" -> {
                check(ctx, saved.getInt("Progress") > 0 && bath.progress == saved.getInt("Progress"),
                    "незавершённый прогресс восстановлен точно");
                check(ctx, bath.batches == saved.getInt("Batches") && bath.items.getStackInSlot(2).isEmpty(),
                    "после перезапуска нет повторной платы");
                check(ctx, topology(bath.items.getStackInSlot(1)).equals(saved.getCompound("Topology")),
                    "топология маски сохранена после перезапуска");
                check(ctx, FluidStack.isSameFluidSameComponents(bath.input.getFluid(),
                    FluidStack.parseOptional(level.registryAccess(), saved.getCompound("Input")))
                    && FluidStack.isSameFluidSameComponents(bath.spent.getFluid(),
                    FluidStack.parseOptional(level.registryAccess(), saved.getCompound("Spent"))),
                    "точные NBT обеих Destroy-смесей восстановлены");
                check(ctx, bath.status() == EtchingStatus.BLOCKED_OUTPUT, "контрольная блокировка выхода сохранена");
            }
            default -> check(ctx, false, "неизвестный сценарий M4 " + testCase);
        }
        reportState(ctx.getSource());
        return report(ctx);
    }

    private static int restartCase(CommandContext<CommandSourceStack> ctx) {
        reset();
        EtchingBathBlockEntity bath = bath(ctx.getSource().getLevel());
        check(ctx, bath.progress > 0 && bath.progress < 400, "перед перезапуском есть незавершённый прогресс");
        bath.spent.setFluid(pure(DestroyMolecules.WATER, EtchingBathBlockEntity.SPENT_CAPACITY));
        CompoundTag saved = source(ctx.getSource().getLevel()).savedM4.copy();
        saved.putInt("Progress", bath.progress);
        saved.putInt("Batches", bath.batches);
        saved.put("Topology", topology(bath.items.getStackInSlot(1)));
        saved.put("Input", bath.input.getFluid().save(ctx.getSource().getLevel().registryAccess()));
        saved.put("Spent", bath.spent.getFluid().save(ctx.getSource().getLevel().registryAccess()));
        source(ctx.getSource().getLevel()).savedM4 = saved;
        source(ctx.getSource().getLevel()).setChanged();
        return report(ctx);
    }

    private static int breakCase(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        level.destroyBlock(BATH, true, null);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(BATH).inflate(2),
            entity -> entity.getItem().is(DestroyExtras.ETCHING_BATH_ITEM.get()));
        check(ctx, drops.size() == 1, "ванна выпала одним предметом");
        if (drops.size() == 1) {
            ItemStack dropped = drops.getFirst().getItem().copy();
            CustomData data = dropped.get(DataComponents.BLOCK_ENTITY_DATA);
            check(ctx, data != null, "предмет ванны содержит BlockEntityData");
            if (data != null) {
                CompoundTag tag = data.copyTag();
                check(ctx, tag.contains("Input") && tag.contains("Spent") && tag.contains("Items")
                    && tag.contains("Progress") && tag.contains("Batches") && tag.contains("Recipe"),
                    "NBT баков, предметов, рецепта и прогресса сохранён");
                check(ctx, tag.getCompound("Items").toString().contains("l1_board"),
                    "готовая L1 сохранена в выпавшей ванне");
                drops.getFirst().discard();
                place(level, BATH, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState());
                net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level, null, BATH, dropped);
                EtchingBathBlockEntity restored = bath(level);
                check(ctx, restored.batches == 1 && restored.spent.getFluidAmount() == 250
                    && L1_RECIPE.equals(restored.recipeId()), "повторная установка восстановила баки и рецепт");
                check(ctx, topology(restored.items.getStackInSlot(2)).equals(source(level).savedM4.getCompound("Topology")),
                    "повторная установка восстановила плату с топологией");
            }
        }
        return report(ctx);
    }

    private static int reportState(CommandSourceStack source) {
        EtchingBathBlockEntity bath = bath(source.getLevel());
        String text = "M4 " + bath.status() + " progress=" + bath.progress + " batches=" + bath.batches
            + " input=" + bath.input.getFluidAmount() + " spent=" + bath.spent.getFluidAmount()
            + " Cu(II)=" + EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.COPPER_II)
            + " Fe(II)=" + EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.IRON_II)
            + " Fe(III)=" + EtchingChemistry.moles(bath.spent.getFluid(), DestroyMolecules.IRON_III);
        source.sendSuccess(() -> Component.literal(text), false);
        return Command.SINGLE_SUCCESS;
    }

    private static CompoundTag topology() {
        CompoundTag topology = new CompoundTag();
        topology.putInt("version", 1);
        topology.putInt("width", 8);
        topology.putInt("height", 8);
        topology.putIntArray("traces", new int[] {1, 2, 5, 8, 13, 21});
        topology.putString("purpose", "m4_topology_roundtrip");
        return topology;
    }

    private static CompoundTag topology(ItemStack stack) {
        CompoundTag pcb = stack.get(PcbItems.PCB_TOPOLOGY.get());
        if (pcb != null) return pcb.copy();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompound("topology");
    }

    private static FluidStack pure(LegacySpecies molecule, int amount) {
        LegacyMixture mixture = new LegacyMixture().addMolecule(molecule, molecule.getPureConcentration());
        mixture.heat((298.15F - mixture.getTemperature()) * mixture.getVolumetricHeatCapacity());
        return MixtureFluid.of(amount, mixture);
    }

    private static LegacyMixture mixture(FluidStack stack) {
        return LegacyMixture.readNBT(stack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
    }

    private static EtchingRecipe recipe(ServerLevel level, ResourceLocation id) {
        if (level.getRecipeManager().byKey(id).orElse(null) instanceof RecipeHolder<?> holder
            && holder.value() instanceof EtchingRecipe recipe) return recipe;
        throw new IllegalStateException("JSON-рецепт M4 не загружен: " + id);
    }

    private static EtchingBathBlockEntity bath(ServerLevel level) {
        if (level.getBlockEntity(BATH) instanceof EtchingBathBlockEntity bath) return bath;
        throw new IllegalStateException("На позиции стенда нет травильной ванны");
    }

    private static TestSourceBlockEntity source(ServerLevel level) {
        if (level.getBlockEntity(SOURCE) instanceof TestSourceBlockEntity source) return source;
        throw new IllegalStateException("На позиции стенда нет сохраняемого состояния M4");
    }

    private static VatControllerBlockEntity vat(ServerLevel level) {
        if (level.getBlockEntity(VAT) instanceof VatControllerBlockEntity vat) return vat;
        throw new IllegalStateException("На позиции стенда нет штатного Vat");
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
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
