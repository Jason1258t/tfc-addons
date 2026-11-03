package tfc.destroy.extras.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import ee.extras.pcb.PcbItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlock;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlockEntity;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;

/** Маршрут игрока без подстановок: порты электролизёра → стенка чана (медь + стекло, свет неба) → вода, железо → ванна. */
final class M10Test {
    private static final BlockPos PROCESS = new BlockPos(40, -60, 10);
    private static final BlockPos VAT = new BlockPos(20, -55, 25);
    private static final BlockPos CENTER = VAT.north(2);
    private static final BlockPos INPUT_WALL = CENTER.east(2);
    private static final BlockPos FLOOR = CENTER.below(2);
    private static final BlockPos BATH = new BlockPos(20, -60, 15);
    private static int checks;
    private static int failures;
    private static FluidStack pendingChlorine = FluidStack.EMPTY;

    private M10Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m10build").executes(M10Test::build))
            .then(Commands.literal("m10gas").executes(M10Test::gas))
            .then(Commands.literal("m10chlorine").executes(M10Test::chlorine))
            .then(Commands.literal("m10water").then(Commands.argument("mb", IntegerArgumentType.integer(1, 1000))
                .executes(c -> water(c, IntegerArgumentType.getInteger(c, "mb")))))
            .then(Commands.literal("m10iron").then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                .executes(c -> iron(c, IntegerArgumentType.getInteger(c, "n")))))
            .then(Commands.literal("m10etch").executes(M10Test::etch))
            .then(Commands.literal("m10migrate").executes(M10Test::migrate))
            .then(Commands.literal("m10status").executes(M10Test::status))
            .then(Commands.literal("m10assert").executes(M10Test::assertBoard)));
    }

    private static int build(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel l = c.getSource().getLevel();
        BlockState wall = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:copper_casing")).defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(CENTER.offset(-2, -2, -2), CENTER.offset(2, 2, 2))) {
            int dx = Math.abs(p.getX() - CENTER.getX()), dy = Math.abs(p.getY() - CENTER.getY()), dz = Math.abs(p.getZ() - CENTER.getZ());
            BlockState s = dx <= 1 && dy <= 1 && dz <= 1 ? Blocks.AIR.defaultBlockState()
                : p.getY() == CENTER.getY() + 2 && dx <= 1 && dz <= 1 ? Blocks.GLASS.defaultBlockState() : wall;
            l.setBlock(p.immutable(), s, Block.UPDATE_ALL);
        }
        l.setBlock(VAT, DestroyBlocks.VAT_CONTROLLER.getDefaultState().setValue(VatControllerBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        VatControllerBlockEntity v = vat(c);
        check(c, v.getVatOptional().isPresent() || v.tryMakeVat(), "чан медь + стекло собран");
        BlockState pipe = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:fluid_pipe")).defaultBlockState();
        l.setBlock(INPUT_WALL.east(), pipe, Block.UPDATE_ALL);
        l.setBlock(FLOOR.below(), pipe, Block.UPDATE_ALL);
        l.setBlock(BATH, DestroyExtras.ETCHING_BATH_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        return report(c);
    }

    /** Одна настоящая партия: водород с севера, хлор снизу, оба в трубу стенки чана. */
    private static int gas(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel l = c.getSource().getLevel();
        FluidStack h2 = l.getCapability(Capabilities.FluidHandler.BLOCK, PROCESS, Direction.NORTH).drain(250, IFluidHandler.FluidAction.EXECUTE);
        FluidStack cl2 = l.getCapability(Capabilities.FluidHandler.BLOCK, PROCESS, Direction.DOWN).drain(250, IFluidHandler.FluidAction.EXECUTE);
        check(c, h2.getAmount() == 250 && DestroyFluids.isMixture(h2), "водород забран с севера как обычная смесь");
        check(c, cl2.getAmount() == 250 && DestroyFluids.isMixture(cl2), "хлор забран снизу как обычная смесь");
        IFluidHandler wall = l.getCapability(Capabilities.FluidHandler.BLOCK, INPUT_WALL, Direction.EAST);
        check(c, wall != null && wall.fill(h2, IFluidHandler.FluidAction.EXECUTE) == 250, "стенка чана приняла водород");
        pendingChlorine = cl2;
        return report(c);
    }

    /** Вход стенки держит одну жидкость: хлор идёт следующей порцией, когда водород ушёл в чан. */
    private static int chlorine(CommandContext<CommandSourceStack> c) {
        reset();
        IFluidHandler wall = c.getSource().getLevel().getCapability(Capabilities.FluidHandler.BLOCK, INPUT_WALL, Direction.EAST);
        check(c, wall.fill(pendingChlorine, IFluidHandler.FluidAction.EXECUTE) == 250, "стенка чана приняла хлор");
        return report(c);
    }

    private static int water(CommandContext<CommandSourceStack> c, int mb) {
        reset();
        ServerLevel l = c.getSource().getLevel();
        IFluidHandler wall = l.getCapability(Capabilities.FluidHandler.BLOCK, INPUT_WALL, Direction.EAST);
        check(c, wall.fill(new FluidStack(Fluids.WATER, mb), IFluidHandler.FluidAction.EXECUTE) == mb, "стенка приняла воду " + mb + " мБ");
        return report(c);
    }

    private static int iron(CommandContext<CommandSourceStack> c, int n) {
        reset();
        IItemHandler wall = c.getSource().getLevel().getCapability(Capabilities.ItemHandler.BLOCK, INPUT_WALL, Direction.EAST);
        ItemStack left = wall == null ? ItemStack.EMPTY : net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(wall, new ItemStack(DestroyItems.IRON_POWDER.get(), n), false);
        check(c, wall != null && left.isEmpty(), "стенка приняла железную пыль");
        return report(c);
    }

    private static int etch(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel l = c.getSource().getLevel();
        EtchingBathBlockEntity bath = (EtchingBathBlockEntity) l.getBlockEntity(BATH);
        IFluidHandler floor = l.getCapability(Capabilities.FluidHandler.BLOCK, FLOOR, Direction.DOWN);
        FluidStack etchant = floor.drain(250, IFluidHandler.FluidAction.EXECUTE);
        say(c, "из дна чана " + etchant.getAmount() + " мБ: " + contents(etchant));
        check(c, etchant.getAmount() == 250 && concentration(etchant, DestroyMolecules.IRON_III) >= 0.024F,
            "через дно забран раствор с Fe(III) от 0,024");
        IFluidHandler port = l.getCapability(Capabilities.FluidHandler.BLOCK, BATH, Direction.WEST);
        check(c, port.fill(etchant, IFluidHandler.FluidAction.EXECUTE) == 250, "западный порт ванны принял раствор");
        bath.items.insertItem(0, new ItemStack(PcbItems.L1_BOARD_BLANK.get()), false);
        ItemStack mask = new ItemStack(PcbItems.L1_MASK.get());
        CompoundTag topology = new CompoundTag();
        topology.putInt("level", 1);
        topology.putString("template", "hand_drawn");
        mask.set(PcbItems.PCB_TOPOLOGY.get(), topology);
        bath.items.insertItem(1, mask, false);
        return report(c);
    }

    private static int assertBoard(CommandContext<CommandSourceStack> c) {
        reset();
        EtchingBathBlockEntity bath = (EtchingBathBlockEntity) c.getSource().getLevel().getBlockEntity(BATH);
        check(c, bath.items.getStackInSlot(2).is(PcbItems.L1_BOARD.get()), "получена плата L1");
        check(c, bath.spent.getFluidAmount() == 250, "отработанный раствор 250 мБ на северном выходе");
        return report(c);
    }

    /** Газ, сохранённый версией 0.2.0 как destroy:gas, после загрузки блока становится обычной смесью. */
    private static int migrate(CommandContext<CommandSourceStack> c) {
        reset();
        ServerLevel l = c.getSource().getLevel();
        MembraneElectrolyzerBlockEntity process = (MembraneElectrolyzerBlockEntity) l.getBlockEntity(PROCESS);
        FluidStack oldGas = new FluidStack(BuiltInRegistries.FLUID.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.FLUID, ResourceLocation.parse("destroy:gas"))), 250,
            MixtureFluid.of(250, new LegacyMixture().addMolecule(DestroyMolecules.CHLORINE, 1.0F)).getComponentsPatch());
        process.chlorine.setFluid(oldGas);
        process.loadCustomOnly(process.saveCustomOnly(l.registryAccess()), l.registryAccess());
        check(c, DestroyFluids.isMixture(process.chlorine.getFluid()) && process.chlorine.getFluidAmount() == 250,
            "старый газ в баке стал обычной смесью");
        check(c, concentration(process.chlorine.getFluid(), DestroyMolecules.CHLORINE) > 0.5F, "состав газа сохранён");
        process.chlorine.setFluid(FluidStack.EMPTY);
        return report(c);
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        VatControllerBlockEntity v = vat(c);
        say(c, "T=" + v.getTemperature() + " P=" + v.getPressure() + " газ=" + contents(v.getGasTankContents())
            + " жидкость=" + v.getLiquidTankContents().getAmount() + " " + contents(v.getLiquidTankContents()));
        return Command.SINGLE_SUCCESS;
    }

    private static VatControllerBlockEntity vat(CommandContext<CommandSourceStack> c) {
        return (VatControllerBlockEntity) c.getSource().getLevel().getBlockEntity(VAT);
    }

    private static float concentration(FluidStack s, LegacySpecies species) {
        return LegacyMixture.readNBT(s.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag())).getConcentrationOf(species);
    }

    private static String contents(FluidStack s) {
        if (s.isEmpty()) return "[]";
        LegacyMixture m = LegacyMixture.readNBT(s.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
        StringBuilder b = new StringBuilder("[");
        for (LegacySpecies x : m.getContents(false)) b.append(x.getFullID()).append('=').append(String.format("%.4f", m.getConcentrationOf(x))).append(' ');
        return b.append(']').toString();
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

    private static void say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal("DESTROYEXTRATEST " + s), false);
    }

    private static int report(CommandContext<CommandSourceStack> c) {
        say(c, (failures == 0 ? "PASS " : "FAIL ") + (checks - failures) + "/" + checks);
        return failures == 0 ? Command.SINGLE_SUCCESS : 0;
    }
}
