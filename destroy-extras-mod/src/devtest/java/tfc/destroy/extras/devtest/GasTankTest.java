package tfc.destroy.extras.devtest;

import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MultiblockOrientation;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import tfc.destroy.extras.DestroyExtras;

final class GasTankTest {
    private static final String[] SIZES = {"compact", "medium", "large"};
    private static final String[] MATERIALS = {"steel", "stainless_steel", "blue_steel"};
    private static final BlockPos LEFT = new BlockPos(1, 100, 6);
    private static final BlockPos RIGHT = LEFT.east(3);

    private GasTankTest() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("gas_tanks").requires(source -> source.hasPermission(2))
                .executes(command -> {
                    var source = command.getSource();
                    try {
                        verify(source.getLevel());
                        source.sendSuccess(() -> Component.literal("DESTROYEXTRATEST PASS: 9 баков, 4 поворота, соседние баки, газ и переполнение"), false);
                        return 1;
                    } catch (IllegalStateException e) {
                        source.sendFailure(Component.literal("DESTROYEXTRATEST FAIL: " + e.getMessage()));
                        return 0;
                    }
                }))
            .then(Commands.literal("gas_tanks_restart").requires(source -> source.hasPermission(2))
                .executes(command -> {
                    var source = command.getSource();
                    var left = handler(source.getLevel(), LEFT.above(), Direction.SOUTH).getFluidInTank(0);
                    var right = handler(source.getLevel(), RIGHT.above(), Direction.SOUTH).getFluidInTank(0);
                    if (left.getAmount() != 250 || right.getAmount() != 500
                        || FluidStack.isSameFluidSameComponents(left, right)) {
                        source.sendFailure(Component.literal("DESTROYEXTRATEST FAIL: из сохранения восстановились неверные запасы"));
                        return 0;
                    }
                    source.sendSuccess(() -> Component.literal("DESTROYEXTRATEST PASS: после рестарта в соседних баках 250 и 500 мБ"), false);
                    return 1;
                })));
    }

    private static void verify(ServerLevel level) {
        int[] volumes = {500, 4000, 16000};
        Direction[] directions = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        var hydrogen = gas("hydrogen", 250);
        var chlorine = gas("chlorine", 500);
        for (int size = 0; size < SIZES.length; size++) {
            for (int material = 0; material < MATERIALS.length; material++) {
                Direction face = directions[(size * 3 + material) % directions.length];
                BlockPos controller = new BlockPos(-11 + material * 7, 100 + size * 7, -8);
                var tank = SIZES[size] + "_" + MATERIALS[material];
                place(level, controller, size, tank, face);
                var input = handler(level, controller, face);
                var output = handler(level, controller.above(), face);
                int capacity = (int) (volumes[size] * 100_000.0 * (2 + material * 2) / (8.3145 * 293));
                check(input.getTankCapacity(0) == capacity, tank + ": неверный предел");
                check(input.fill(hydrogen.copyWithAmount(capacity + 1), FluidAction.SIMULATE) == capacity
                    && output.getFluidInTank(0).isEmpty(), tank + ": симуляция изменила бак");
                check(input.fill(hydrogen.copyWithAmount(capacity + 1), FluidAction.EXECUTE) == capacity,
                    tank + ": ошибка ограничения давления");
                check(output.fill(hydrogen, FluidAction.EXECUTE) == 0 && input.drain(1, FluidAction.EXECUTE).isEmpty(),
                    tank + ": порт работает в неверном направлении");
                check(output.drain(capacity - 1, FluidAction.EXECUTE).getAmount() == capacity - 1,
                    tank + ": ошибка выдачи газа");
                check(input.fill(chlorine, FluidAction.EXECUTE) == 0, tank + ": смешались водород и хлор");
                var result = output.drain(1, FluidAction.EXECUTE);
                check(result.getAmount() == 1 && result.get(DestroyDataComponents.MIXTURE)
                    .getList("Contents", 10).getCompound(0).getFloat("Gaseous") == 1, tank + ": потерялся газ");
                check(input.fill(chlorine, FluidAction.EXECUTE) == 500, tank + ": пустой бак не сменил газ");
            }
        }

        place(level, LEFT, 2, "large_steel", Direction.SOUTH);
        place(level, RIGHT, 2, "large_steel", Direction.SOUTH);
        check(handler(level, LEFT, Direction.SOUTH).fill(hydrogen, FluidAction.EXECUTE) == 250,
            "левый бак не принял водород");
        check(handler(level, RIGHT, Direction.SOUTH).fill(chlorine, FluidAction.EXECUTE) == 500,
            "правый бак не принял хлор");
        check(handler(level, LEFT.above(), Direction.SOUTH).getFluidInTank(0).getAmount() == 250
            && handler(level, RIGHT.above(), Direction.SOUTH).getFluidInTank(0).getAmount() == 500,
            "соседние баки смешали запасы");
    }

    private static void place(ServerLevel level, BlockPos controller, int size, String tank, Direction face) {
        var id = DestroyExtras.rl("multiblocks/gas_tank_" + tank);
        var structure = MultiblockHandler.getByUniqueName(id);
        check(structure != null, "не зарегистрирован " + id);
        var relativeController = new BlockPos(size / 2, 0, size);
        var orientation = new MultiblockOrientation(face.getOpposite(), false);
        for (var cell : structure.getStructure(level)) {
            var pos = controller.offset(orientation.getAbsoluteOffset(cell.pos().subtract(relativeController)));
            level.setBlock(pos, cell.state(), Block.UPDATE_ALL);
        }
        check(structure.createStructure(level, controller, face, null), "не собрался " + tank + " со стороны " + face);
    }

    private static IFluidHandler handler(ServerLevel level, BlockPos pos, Direction face) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        check(handler != null, "нет порта " + pos.toShortString() + " " + face);
        return handler;
    }

    private static FluidStack gas(String species, int amount) {
        var component = new CompoundTag();
        component.putString("Molecule", "destroy:" + species);
        component.putFloat("Concentration", 1);
        var contents = new ListTag();
        contents.add(component);
        var mixture = new CompoundTag();
        mixture.put("Contents", contents);
        var fluid = new FluidStack(DestroyFluids.MIXTURE.get().getSource(), amount);
        fluid.set(DestroyDataComponents.MIXTURE, mixture);
        return fluid;
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new IllegalStateException(message);
    }
}
