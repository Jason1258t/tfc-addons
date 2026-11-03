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
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import ee.extras.pcb.PcbItems;
import ee.extras.pcb.PcbMachineBlockEntity;
import ee.extras.pcb.PcbMachineDevice;
import ee.extras.pcb.PcbMachineStatus;
import ee.extras.pcb.PcbProcessRecipe;
import ee.extras.pcb.PcbTopology;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;
import tfc.destroy.extras.etching.EtchingChemistry;
import tfc.destroy.extras.recipe.EtchingRecipe;

/** Сквозной Survival-рецептный срез M11: EE → фотолитография → FeCl₃ → металлизация via. */
final class M11Test {
    private static final BlockPos SOURCE = new BlockPos(58, -60, 20);
    private static final BlockPos PHOTO = new BlockPos(60, -60, 20);
    private static final BlockPos PHOTO_BATTERY = PHOTO.east(2);
    private static final BlockPos METAL = new BlockPos(60, -60, 24);
    private static final BlockPos METAL_BATTERY = METAL.east(2);
    private static final BlockPos BATH = new BlockPos(60, -60, 28);
    private static int checks;
    private static int failures;

    private M11Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m11environment").executes(M11Test::environment))
            .then(Commands.literal("m11build").executes(M11Test::build))
            .then(Commands.literal("m11load").then(Commands.argument("level", IntegerArgumentType.integer(2, 3))
                .executes(c -> load(c, IntegerArgumentType.getInteger(c, "level"), "normal"))))
            .then(Commands.literal("m11fault").then(Commands.argument("case", StringArgumentType.word())
                .executes(c -> load(c, 2, StringArgumentType.getString(c, "case")))))
            .then(Commands.literal("m11power").then(Commands.argument("machine", StringArgumentType.word())
                .then(Commands.argument("volts", DoubleArgumentType.doubleArg(0))
                    .executes(c -> power(c, StringArgumentType.getString(c, "machine"),
                        DoubleArgumentType.getDouble(c, "volts"))))))
            .then(Commands.literal("m11etch").executes(M11Test::etch))
            .then(Commands.literal("m11metal").executes(M11Test::metal))
            .then(Commands.literal("m11restartcase").executes(M11Test::restartCase))
            .then(Commands.literal("m11sag").executes(M11Test::sagCase))
            .then(Commands.literal("m11break").executes(M11Test::breakCase))
            .then(Commands.literal("m11report").executes(c -> {
                EtchingBathBlockEntity bath = bath(c.getSource().getLevel());
                say(c, "M11 ванна " + bath.status() + " recipe=" + bath.recipeId() + " progress=" + bath.progress
                    + " batches=" + bath.batches + " rpm=" + bath.mixerRpm() + " input=" + bath.input.getFluidAmount()
                    + " spent=" + bath.spent.getFluidAmount() + " FeIII="
                    + EtchingChemistry.moles(bath.input.getFluid(), DestroyMolecules.IRON_III));
                ItemStack mask = bath.items.getStackInSlot(1);
                CompoundTag topology = mask.get(PcbItems.PCB_TOPOLOGY.get());
                EtchingRecipe recipe = (EtchingRecipe) c.getSource().getLevel().getRecipeManager().byKey(bath.recipeId()).orElseThrow().value();
                say(c, "M11 входы valid=" + recipe.validItems(bath.items.getStackInSlot(0), mask)
                    + " blank=" + recipe.blank().test(bath.items.getStackInSlot(0)) + " mask=" + recipe.mask().test(mask)
                    + " industrial=" + recipe.industrialLevel() + " design=" + mask.getItem()
                    + " validated=" + (topology != null && topology.getBoolean("Validated"))
                    + " level=" + (topology == null ? 0 : PcbTopology.level(topology))
                    + " error=" + (topology == null ? "null" : PcbTopology.validate(topology)));
                FluidStack consumed = bath.input.getFluid().copyWithAmount(250);
                FluidStack spentResult = EtchingChemistry.spentEtchant(consumed, 250, recipe.copperMoles());
                say(c, "M11 раствор ingredientAfter=" + recipe.input().test(consumed)
                    + " spentEmpty=" + spentResult.isEmpty() + " result=" + recipe.result(mask)
                    + " atomic=" + tfc.destroy.bridge.fluid.AtomicFluidOperation.canExecute(bath.input, consumed,
                        new net.neoforged.neoforge.fluids.capability.templates.FluidTank[] {bath.spent},
                        new FluidStack[] {spentResult}));
                PcbMachineDevice device = photo(c.getSource().getLevel()).device();
                say(c, "M11 питание V=" + device.measuredVoltage() + " A=" + device.measuredCurrent());
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("m11assert").then(Commands.argument("case", StringArgumentType.word())
                .executes(c -> assertCase(c, StringArgumentType.getString(c, "case"))))));
    }

    private static int environment(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        for (int pcbLevel : new int[] {2, 3}) {
            double volts = pcbLevel == 2 ? 240 : 600;
            double amps = pcbLevel == 2 ? 4 : 8;
            int duration = pcbLevel == 2 ? 300 : 450;
            for (String machine : new String[] {"photolithography", "metallization"}) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ee_extras",
                    "pcb/" + machine + "_l" + pcbLevel);
                if (level.getRecipeManager().byKey(id).orElse(null) instanceof RecipeHolder<?> holder
                    && holder.value() instanceof PcbProcessRecipe recipe) {
                    check(c, recipe.level() == pcbLevel && recipe.voltage() == volts && recipe.current() == amps
                        && recipe.getProcessingDuration() == duration, id + ": параметры M11 загружены");
                } else check(c, false, "не загружен " + id);
            }
            ResourceLocation id = DestroyExtras.rl("etching/l" + pcbLevel + "_board");
            if (level.getRecipeManager().byKey(id).orElse(null) instanceof RecipeHolder<?> holder
                && holder.value() instanceof EtchingRecipe recipe) {
                check(c, recipe.industrialLevel() == pcbLevel && recipe.copperMoles() == pcbLevel * 3.0F
                    && recipe.input().amount() == 250, id + ": многоразовый чертёж и химия M11 загружены");
            } else check(c, false, "не загружен " + id);
        }
        check(c, level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("ee_extras", "pcb/photoresist")).isPresent()
            && level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("ee_extras", "pcb/photolithographer")).isPresent()
            && level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("ee_extras", "pcb/via_metallizer")).isPresent(),
            "Survival-крафты фоторезиста и обеих машин загружены");
        return report(c);
    }

    private static int build(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        place(level, SOURCE, DestroyExtrasTest.SOURCE_BLOCK.get().defaultBlockState());
        place(level, PHOTO, PcbItems.PHOTOLITHOGRAPHER_BLOCK.get().defaultBlockState());
        place(level, METAL, PcbItems.METALLIZER_BLOCK.get().defaultBlockState());
        place(level, PHOTO_BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        place(level, METAL_BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        place(level, BATH, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState());
        wire(level, PHOTO_BATTERY, 0, PHOTO, PcbMachineDevice.PLUS);
        wire(level, PHOTO_BATTERY, 1, PHOTO, PcbMachineDevice.MINUS);
        wire(level, METAL_BATTERY, 0, METAL, PcbMachineDevice.PLUS);
        wire(level, METAL_BATTERY, 1, METAL, PcbMachineDevice.MINUS);
        power(level, PHOTO_BATTERY, 240);
        power(level, METAL_BATTERY, 240);
        mixer(level);
        check(c, photo(level).device() != null && metal(level).device() != null, "обе машины создали EE-драйверы");
        check(c, level.getCapability(Capabilities.ItemHandler.BLOCK, PHOTO, Direction.UP)
            .insertItem(3, new ItemStack(PcbItems.L1_BOARD.get()), true).getCount() == 1,
            "item handler запрещает вставку в выходной слот");
        return report(c);
    }

    private static int load(CommandContext<CommandSourceStack> c, int levelNumber, String fault) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        clear(level);
        double volts = levelNumber == 2 ? 240 : 600;
        power(level, PHOTO_BATTERY, volts);
        power(level, METAL_BATTERY, volts);
        ItemStack design = new ItemStack(PcbItems.PCB_DESIGN.get());
        CompoundTag topology = topology(levelNumber);
        if (fault.equals("invalid")) topology = PcbTopology.create(levelNumber);
        PcbTopology.attach(design, topology);
        source(level).savedM4 = new CompoundTag();
        source(level).savedM4.put("Topology", design.get(PcbItems.PCB_TOPOLOGY.get()).copy());
        source(level).savedM4.putInt("Level", levelNumber);
        source(level).setChanged();
        photo(level).items.setStackInSlot(0, new ItemStack(levelNumber == 2
            ? PcbItems.L2_BOARD_BLANK.get() : PcbItems.L3_BOARD_BLANK.get()));
        photo(level).items.setStackInSlot(1, design);
        if (!fault.equals("missing")) photo(level).items.setStackInSlot(2, new ItemStack(PcbItems.PHOTORESIST.get()));
        if (fault.equals("blocked")) photo(level).items.setStackInSlot(3, new ItemStack(PcbItems.L1_BOARD.get()));
        if (fault.equals("undervoltage")) power(level, PHOTO_BATTERY, 200);
        say(c, "M11 подготовлен сценарий " + fault + " L" + levelNumber);
        return Command.SINGLE_SUCCESS;
    }

    private static int etch(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        int pcbLevel = source(level).savedM4.getInt("Level");
        ItemStack prepared = photo(level).items.getStackInSlot(3);
        check(c, prepared.is(pcbLevel == 2 ? PcbItems.L2_PREPARED_BLANK.get() : PcbItems.L3_PREPARED_BLANK.get()),
            "фотолитограф выдал подготовленную заготовку");
        bath(level).items.setStackInSlot(0, prepared.copy());
        bath(level).items.setStackInSlot(1, photo(level).items.getStackInSlot(1).copy());
        bath(level).input.setFluid(etchant(pcbLevel));
        bath(level).spent.setFluid(FluidStack.EMPTY);
        return report(c);
    }

    private static int metal(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        int pcbLevel = source(level).savedM4.getInt("Level");
        ItemStack etched = bath(level).items.getStackInSlot(2);
        check(c, etched.is(pcbLevel == 2 ? PcbItems.L2_ETCHED_BOARD.get() : PcbItems.L3_ETCHED_BOARD.get()),
            "ванна выдала протравленную плату");
        metal(level).items.setStackInSlot(0, etched.copy());
        metal(level).items.setStackInSlot(1, new ItemStack(PcbItems.COPPER_FOIL.get()));
        return report(c);
    }

    private static int assertCase(CommandContext<CommandSourceStack> c, String testCase) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        int pcbLevel = source(level).savedM4.getInt("Level");
        CompoundTag expected = source(level).savedM4.getCompound("Topology");
        switch (testCase) {
            case "photo" -> {
                ItemStack result = photo(level).items.getStackInSlot(3);
                check(c, result.is(pcbLevel == 2 ? PcbItems.L2_PREPARED_BLANK.get() : PcbItems.L3_PREPARED_BLANK.get())
                    && result.getCount() == 1 && photo(level).batches == 1, "фотолитография выполнила ровно одну партию");
                check(c, photo(level).items.getStackInSlot(0).isEmpty() && photo(level).items.getStackInSlot(2).isEmpty()
                    && photo(level).items.getStackInSlot(1).getCount() == 1, "заготовка и фоторезист израсходованы, чертёж сохранён");
                check(c, sameTopology(result, expected, pcbLevel), "подготовленная заготовка сохранила все слои и via");
            }
            case "photo_power" -> checkPower(c, photo(level), pcbLevel);
            case "metal_power" -> checkPower(c, metal(level), pcbLevel);
            case "etch" -> {
                ItemStack result = bath(level).items.getStackInSlot(2);
                check(c, result.is(pcbLevel == 2 ? PcbItems.L2_ETCHED_BOARD.get() : PcbItems.L3_ETCHED_BOARD.get())
                    && bath(level).batches == 1, "production-ванна выполнила одно травление");
                check(c, bath(level).items.getStackInSlot(0).isEmpty() && bath(level).items.getStackInSlot(1).getCount() == 1,
                    "подготовленная заготовка израсходована, чертёж не расходуется");
                check(c, sameTopology(result, expected, pcbLevel), "травление сохранило точную топологию и via");
                check(c, bath(level).spent.getFluidAmount() == 250
                    && close(EtchingChemistry.moles(bath(level).spent.getFluid(), DestroyMolecules.COPPER_II), pcbLevel * 3, 0.001)
                    && close(EtchingChemistry.moles(bath(level).spent.getFluid(), DestroyMolecules.IRON_II), pcbLevel * 6, 0.001),
                    "химия соответствует числу слоёв без новых жидкостей");
            }
            case "board" -> {
                ItemStack result = metal(level).items.getStackInSlot(3);
                check(c, result.is(pcbLevel == 2 ? PcbItems.L2_BOARD.get() : PcbItems.L3_BOARD.get())
                    && result.getCount() == 1 && metal(level).batches == 1, "получена одна готовая плата L" + pcbLevel);
                check(c, metal(level).items.getStackInSlot(0).isEmpty() && metal(level).items.getStackInSlot(1).isEmpty(),
                    "протравленная плата и медная фольга израсходованы без остатка");
                check(c, sameTopology(result, expected, pcbLevel), "готовая плата сохранила топологию всех слоёв и via");
            }
            case "invalid", "missing", "blocked", "undervoltage" -> {
                PcbMachineBlockEntity machine = photo(level);
                if (testCase.equals("invalid")) check(c, machine.status() == PcbMachineStatus.INVALID_DESIGN,
                    "повреждённый проект явно отклонён");
                if (testCase.equals("blocked")) check(c, machine.status() == PcbMachineStatus.BLOCKED_OUTPUT,
                    "заполненный выход останавливает операцию");
                if (testCase.equals("undervoltage")) check(c, machine.status() == PcbMachineStatus.NO_POWER,
                    "200 В вместо 240 В не запускает L2");
                check(c, machine.batches == 0 && machine.progress == 0 && machine.items.getStackInSlot(0).getCount() == 1
                    && machine.items.getStackInSlot(1).getCount() == 1, "ошибка не расходует предметы и не создаёт прогресс");
                if (!testCase.equals("missing")) check(c, machine.items.getStackInSlot(2).getCount() == 1,
                    "фоторезист сохранён при ошибке");
            }
            case "sag_settled" -> {
                PcbMachineBlockEntity machine = photo(level);
                check(c, machine.status() == PcbMachineStatus.NO_POWER && !machine.device().hasWorkingPower(),
                    "после переходного тика EE машина видит просадку напряжения");
                source(level).savedM4.putInt("SagProgress", machine.progress);
                source(level).setChanged();
            }
            case "sag" -> {
                PcbMachineBlockEntity machine = photo(level);
                check(c, machine.progress == source(level).savedM4.getInt("SagProgress") && machine.batches == 0,
                    "после просадки 240 → 200 В прогресс сохранён и не растёт");
                check(c, machine.status() == PcbMachineStatus.NO_POWER && !machine.device().hasWorkingPower(),
                    "просадка переводит работающую машину в состояние без питания");
                check(c, machine.items.getStackInSlot(0).getCount() == 1 && machine.items.getStackInSlot(1).getCount() == 1
                    && machine.items.getStackInSlot(2).getCount() == 1 && machine.items.getStackInSlot(3).isEmpty(),
                    "просадка не расходует входы и не создаёт выход");
            }
            case "persisted" -> {
                CompoundTag saved = source(level).savedM4;
                check(c, saved.getInt("Progress") > 0 && photo(level).progress == saved.getInt("Progress"),
                    "прогресс фотолитографа восстановлен после restart");
                check(c, photo(level).batches == saved.getInt("Batches") && photo(level).items.getStackInSlot(3).isEmpty(),
                    "restart не повторил партию и не создал выход");
                check(c, sameTopology(photo(level).items.getStackInSlot(1), expected, pcbLevel),
                    "чертёж и его via сохранены после restart");
                check(c, ResourceLocation.fromNamespaceAndPath("ee_extras", "pcb/photolithography_l" + pcbLevel)
                    .equals(photo(level).recipeId()), "id production-рецепта восстановлен");
            }
            default -> check(c, false, "неизвестный сценарий M11 " + testCase);
        }
        return report(c);
    }

    private static int restartCase(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        check(c, photo(level).progress > 0 && photo(level).progress < 300, "перед restart есть незавершённая партия");
        source(level).savedM4.putInt("Progress", photo(level).progress);
        source(level).savedM4.putInt("Batches", photo(level).batches);
        source(level).setChanged();
        power(level, PHOTO_BATTERY, 0);
        return report(c);
    }

    private static int sagCase(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        check(c, photo(level).progress > 0 && photo(level).progress < 300, "перед просадкой есть незавершённая партия");
        source(level).savedM4.putInt("SagProgress", photo(level).progress);
        source(level).setChanged();
        power(level, PHOTO_BATTERY, 200);
        return report(c);
    }

    private static int breakCase(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel level = c.getSource().getLevel();
        level.destroyBlock(PHOTO, true, null);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(PHOTO).inflate(2),
            entity -> entity.getItem().is(PcbItems.PHOTOLITHOGRAPHER_ITEM.get()));
        check(c, drops.size() == 1, "фотолитограф выпал одним предметом без дюпа");
        if (drops.size() == 1) {
            ItemStack dropped = drops.getFirst().getItem().copy();
            CustomData data = dropped.get(DataComponents.BLOCK_ENTITY_DATA);
            check(c, data != null && data.copyTag().contains("Items") && data.copyTag().contains("Recipe"),
                "выпавший блок сохранил предметы, прогресс и рецепт");
            drops.getFirst().discard();
            place(level, PHOTO, PcbItems.PHOTOLITHOGRAPHER_BLOCK.get().defaultBlockState());
            net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level, null, PHOTO, dropped);
            check(c, photo(level).batches == 1 && photo(level).items.getStackInSlot(1).getCount() == 1
                && sameTopology(photo(level).items.getStackInSlot(3), source(level).savedM4.getCompound("Topology"),
                    source(level).savedM4.getInt("Level")), "повторная установка восстановила чертёж и результат без дюпа");
        }
        return report(c);
    }

    private static void clear(ServerLevel level) {
        for (PcbMachineBlockEntity machine : new PcbMachineBlockEntity[] {photo(level), metal(level)}) {
            for (int slot = 0; slot < 4; slot++) machine.items.setStackInSlot(slot, ItemStack.EMPTY);
            machine.progress = 0;
            machine.batches = 0;
        }
        for (int slot = 0; slot < 3; slot++) bath(level).items.setStackInSlot(slot, ItemStack.EMPTY);
        bath(level).input.setFluid(FluidStack.EMPTY);
        bath(level).spent.setFluid(FluidStack.EMPTY);
        bath(level).progress = 0;
        bath(level).batches = 0;
    }

    private static int power(CommandContext<CommandSourceStack> c, String machine, double volts) {
        power(c.getSource().getLevel(), machine.equals("photo") ? PHOTO_BATTERY : METAL_BATTERY, volts);
        say(c, "M11 питание " + machine + ": " + volts + " В");
        return Command.SINGLE_SUCCESS;
    }

    private static void power(ServerLevel level, BlockPos pos, double volts) {
        CreativeBatteryDevice battery = DevicesSavedData.load(level).getDevice(pos, CreativeBatteryDevice.class);
        if (battery == null) throw new IllegalStateException("Нет EE-батареи M11");
        battery.voltage = volts;
        battery.acFrequency = 0;
        DevicesSavedData.load(level).setDirty();
    }

    private static void checkPower(CommandContext<CommandSourceStack> c, PcbMachineBlockEntity machine, int pcbLevel) {
        PcbMachineDevice device = machine.device();
        check(c, device != null && device.hasWorkingPower()
            && close(device.measuredVoltage(), pcbLevel == 2 ? 240 : 600, 0.25)
            && close(device.measuredCurrent(), pcbLevel == 2 ? 4 : 8, 0.1),
            "во время работы настоящая EE-сеть держит номинал L" + pcbLevel);
    }

    private static void mixer(ServerLevel level) {
        place(level, BATH.above(), AllBlocks.MECHANICAL_MIXER.getDefaultState());
        BlockPos drive = BATH.above().east();
        place(level, drive, AllBlocks.COGWHEEL.getDefaultState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Y));
        place(level, drive.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.DOWN));
        if (level.getBlockEntity(drive.above()) instanceof CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(64);
    }

    private static CompoundTag topology(int level) {
        CompoundTag tag = PcbTopology.create(level);
        PcbTopology.set(tag, 0, 0, PcbTopology.TERMINAL);
        for (int cell = 1; cell <= 8; cell++) PcbTopology.set(tag, 0, cell, PcbTopology.COMPONENT);
        PcbTopology.set(tag, 0, 9, PcbTopology.TRACE);
        PcbTopology.set(tag, 0, 10, PcbTopology.VIA);
        PcbTopology.set(tag, 0, 11, PcbTopology.TERMINAL);
        for (int layer = 1; layer < level; layer++) PcbTopology.set(tag, layer, 10, PcbTopology.VIA);
        if (PcbTopology.validate(tag) != null) throw new IllegalStateException(PcbTopology.validate(tag));
        return tag;
    }

    private static boolean sameTopology(ItemStack stack, CompoundTag expected, int level) {
        CompoundTag actual = stack.get(PcbItems.PCB_TOPOLOGY.get());
        return actual != null && actual.equals(expected) && PcbTopology.level(actual) == level
            && PcbTopology.get(actual, 0, 10) == PcbTopology.VIA
            && PcbTopology.get(actual, level - 1, 10) == PcbTopology.VIA;
    }

    private static FluidStack etchant(int pcbLevel) {
        float ferricChloride = pcbLevel * 0.024F;
        LegacyMixture mixture = new LegacyMixture()
            .addMolecule(DestroyMolecules.IRON_III, ferricChloride)
            .addMolecule(DestroyMolecules.CHLORIDE, ferricChloride * 3)
            .addMolecule(DestroyMolecules.WATER, 50.0F);
        mixture.heat((298.15F - mixture.getTemperature()) * mixture.getVolumetricHeatCapacity());
        FluidStack stack = MixtureFluid.of(250, mixture);
        if (stack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()).isEmpty())
            throw new IllegalStateException("Не создана Destroy-смесь M11");
        return stack;
    }

    private static void wire(ServerLevel level, BlockPos a, int nodeA, BlockPos b, int nodeB) {
        for (BlockPos pos : new BlockPos[] {a, b}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ElectricalDeviceBlock<?> block) block.ensureNodesExist(level, pos, state);
        }
        InfrastructureSavedData.load(level).connect(new InWorldNode(nodeA, a), new InWorldNode(nodeB, b),
            CEEWireTypes.COPPER.get());
    }

    private static PcbMachineBlockEntity photo(ServerLevel level) {
        return (PcbMachineBlockEntity) level.getBlockEntity(PHOTO);
    }

    private static PcbMachineBlockEntity metal(ServerLevel level) {
        return (PcbMachineBlockEntity) level.getBlockEntity(METAL);
    }

    private static EtchingBathBlockEntity bath(ServerLevel level) {
        return (EtchingBathBlockEntity) level.getBlockEntity(BATH);
    }

    private static TestSourceBlockEntity source(ServerLevel level) {
        return (TestSourceBlockEntity) level.getBlockEntity(SOURCE);
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

    private static void check(CommandContext<CommandSourceStack> c, boolean ok, String label) {
        checks++;
        if (!ok) failures++;
        say(c, (ok ? "OK " : "FAIL ") + label);
    }

    private static void say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSystemMessage(Component.literal("DESTROYEXTRATEST " + text));
    }

    private static int report(CommandContext<CommandSourceStack> c) {
        say(c, (failures == 0 && checks > 0 ? "PASS " : "FAIL ") + (checks - failures) + "/" + checks);
        return failures == 0 && checks > 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
