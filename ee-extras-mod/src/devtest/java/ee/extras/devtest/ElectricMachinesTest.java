package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import ee.extras.EeExtras;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;

final class ElectricMachinesTest {
    private static final BlockPos MIXER = new BlockPos(5, -57, 5);
    private static final BlockPos DEPLOYER = new BlockPos(15, -58, 5);
    private static final BlockPos DISPENSER = new BlockPos(24, -57, 5);
    private static final BlockPos TARGET = DISPENSER.below();

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("electricmachinestest")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
                .then(Commands.literal("feed").executes(ctx -> feed(ctx.getSource())))
                .then(Commands.literal("wire").executes(ctx -> wire(ctx.getSource())))
                .then(Commands.literal("mechanics").executes(ctx -> mechanics(ctx.getSource())))
                .then(Commands.literal("lavafilter").executes(ctx -> filter(ctx.getSource(), new ItemStack(Items.LAVA_BUCKET))))
                .then(Commands.literal("waterfilter").executes(ctx -> filter(ctx.getSource(), new ItemStack(Items.WATER_BUCKET))))
                .then(Commands.literal("dose").then(Commands.argument("mb", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> dose(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "mb")))))
                .then(Commands.literal("volume").then(Commands.argument("mb", IntegerArgumentType.integer(0, 1000))
                        .executes(ctx -> volume(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "mb"))))));
    }

    private static int build(CommandSourceStack source) {
        var level = source.getLevel();
        DevTest.place(level, MIXER.below(2), AllBlocks.BASIN.getDefaultState());
        DevTest.place(level, MIXER, EeExtras.ELECTRIC_MIXER.get().defaultBlockState());
        DevTest.place(level, DEPLOYER, EeExtras.ELECTRIC_DEPLOYER.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        DevTest.place(level, DEPLOYER.east(2).below(), Blocks.STONE.defaultBlockState());
        for (BlockPos machine : new BlockPos[] {MIXER, DEPLOYER}) {
            DevTest.place(level, machine.south(3), CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        }
        DevTest.place(level, TARGET, AllBlocks.FLUID_TANK.getDefaultState());
        DevTest.place(level, DISPENSER, EeExtras.FLUID_DISPENSER.get().defaultBlockState());
        return 1;
    }

    private static int feed(CommandSourceStack source) {
        var level = source.getLevel();
        IItemHandler basin = level.getCapability(Capabilities.ItemHandler.BLOCK, MIXER.below(2), Direction.UP);
        IItemHandler deployer = level.getCapability(Capabilities.ItemHandler.BLOCK, DEPLOYER, Direction.UP);
        var water = new FluidStack(Fluids.WATER, 1000);
        var basinTank = level.getCapability(Capabilities.FluidHandler.BLOCK, MIXER.below(2), Direction.UP);
        var dispenser = level.getCapability(Capabilities.FluidHandler.BLOCK, DISPENSER, Direction.UP);
        boolean fed = basin != null && basin.insertItem(0, AllItems.WHEAT_FLOUR.asStack(), false).isEmpty()
                && basinTank != null && basinTank.fill(water, FluidAction.EXECUTE) == 1000
                && deployer != null && deployer.insertItem(0, new ItemStack(Items.COBBLESTONE), false).isEmpty()
                && dispenser != null && dispenser.fill(water, FluidAction.EXECUTE) == 1000;
        return check(source, fed, "мука и вода в чаше, булыжник в руке активатора, вода в дозаторе");
    }

    private static int wire(CommandSourceStack source) {
        var level = source.getLevel();
        for (BlockPos machine : new BlockPos[] {MIXER, DEPLOYER}) {
            BlockPos battery = machine.south(3);
            DevTest.wire(level, battery, 0, machine, 0);
            DevTest.wire(level, battery, 1, machine, 1);
            var power = DevicesSavedData.load(level).getDevice(battery, CreativeBatteryDevice.class);
            if (power == null) return check(source, false, "батарея зарегистрирована");
            power.voltage = 330;
        }
        return 1;
    }

    private static int mechanics(CommandSourceStack source) {
        var level = source.getLevel();
        boolean speed = true;
        for (BlockPos machine : new BlockPos[] {MIXER, DEPLOYER}) {
            speed &= level.getBlockEntity(machine) instanceof KineticBlockEntity be
                    && be.getSpeed() == 32 && !be.hasNetwork();
        }
        IItemHandler basin = level.getCapability(Capabilities.ItemHandler.BLOCK, MIXER.below(2), Direction.DOWN);
        boolean dough = false;
        if (basin != null) {
            for (int i = 0; i < basin.getSlots(); i++) dough |= AllItems.DOUGH.isIn(basin.getStackInSlot(i));
        }
        boolean placed = level.getBlockState(DEPLOYER.east(2)).is(Blocks.COBBLESTONE);
        return check(source, speed && dough && placed,
                "32 об/мин без валов: " + speed + ", тесто: " + dough + ", активатор поставил блок: " + placed);
    }

    private static int filter(CommandSourceStack source, ItemStack stack) {
        var filter = BlockEntityBehaviour.get(source.getLevel(), DISPENSER, FilteringBehaviour.TYPE);
        return check(source, filter != null && filter.setFilter(stack), "установлен фильтр жидкости");
    }

    private static int dose(CommandSourceStack source, int amount) {
        var setting = BlockEntityBehaviour.get(source.getLevel(), DISPENSER, ScrollValueBehaviour.TYPE);
        if (setting == null) return check(source, false, "настройка дозы доступна");
        setting.setValue(amount);
        return check(source, setting.getValue() == amount, "доза " + amount);
    }

    private static int volume(CommandSourceStack source, int amount) {
        var tank = source.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, TARGET, Direction.UP);
        int actual = tank == null ? -1 : tank.getFluidInTank(0).getAmount();
        return check(source, actual == amount, "объём снизу " + actual + " мБ, ожидалось " + amount);
    }

    private static int check(CommandSourceStack source, boolean ok, String message) {
        if (ok) source.sendSuccess(() -> Component.literal("PASS: " + message), false);
        else source.sendFailure(Component.literal("FAIL: " + message));
        return ok ? 1 : 0;
    }
}
