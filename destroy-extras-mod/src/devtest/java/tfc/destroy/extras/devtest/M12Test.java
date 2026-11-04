package tfc.destroy.extras.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.AllBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.phys.Vec3;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import net.minecraft.world.level.material.Fluids;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ElectrolyzerStructure;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlock;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlockEntity;

/** Поворот электролизёра ключом: порты, узлы EE, корпус и сохранение жидкости. */
final class M12Test {
    private static final BlockPos ROT = new BlockPos(8, -60, -6);
    private static int checks;
    private static int failures;

    private M12Test() {}

    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("destroyextratest")
            .then(Commands.literal("m12build").then(Commands.argument("facing", StringArgumentType.word())
                .executes(ctx -> build(ctx.getSource(), facing(ctx)))))
            .then(Commands.literal("m12assert").then(Commands.argument("facing", StringArgumentType.word())
                .executes(ctx -> assertFacing(ctx, facing(ctx)))))
            .then(Commands.literal("m12rotate").executes(M12Test::rotateWithWrench))
            .then(Commands.literal("m12assertrotated").executes(M12Test::assertRotated)));
    }

    private static Direction facing(CommandContext<CommandSourceStack> ctx) {
        return switch (StringArgumentType.getString(ctx, "facing")) {
            case "north" -> Direction.NORTH;
            case "east" -> Direction.EAST;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            default -> throw new IllegalArgumentException("сторона: north|east|south|west");
        };
    }

    /** Собирает корпус под выбранную ориентацию: блок и медный/трубный каркас повёрнуты вместе. */
    private static int build(CommandSourceStack source, Direction facing) {
        ServerLevel level = source.getLevel();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    level.setBlock(ROT.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(ROT, DestroyExtras.ELECTROLYZER_BLOCK.get().defaultBlockState()
            .setValue(MembraneElectrolyzerBlock.FACING, facing), 3);
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 1; z++) {
                    if ((y == 0 && z == 0) || (x == 1 && y == 1 && z == 1)) continue;
                    BlockState state = x != 0 ? AllBlocks.FLUID_TANK.getDefaultState()
                        : y == 1 && z == 1 ? AllBlocks.MECHANICAL_PUMP.getDefaultState()
                        : AllBlocks.COPPER_CASING.getDefaultState();
                    level.setBlock(ElectrolyzerStructure.offset(ROT, facing, x, y, z), state, 3);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("M12 собран: " + facing), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int assertFacing(CommandContext<CommandSourceStack> ctx, Direction facing) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        MembraneElectrolyzerBlockEntity process = process(level);
        check(ctx, level.getBlockState(ROT).getValue(MembraneElectrolyzerBlock.FACING) == facing, "блок смотрит на " + facing);
        check(ctx, process.formed(), "корпус собран под поворот " + facing);
        // Мировые стороны для каждой ориентации: выход H₂ — перед, Cl₂ — низ и задняя, NaOH — слева от передней грани, вход — справа.
        Direction h2 = facing;
        Direction naoh = facing.getClockWise();
        Direction cl2 = facing.getOpposite();
        Direction input = facing.getCounterClockWise();
        check(ctx, cap(level, h2) == process.hydrogen, "сторона " + h2 + " — H₂");
        check(ctx, cap(level, naoh) == process.sodiumHydroxide, "сторона " + naoh + " — NaOH");
        check(ctx, cap(level, cl2) == process.chlorine, "сторона " + cl2 + " — Cl₂");
        check(ctx, cap(level, Direction.DOWN) == process.chlorine, "низ — Cl₂ при любой ориентации");
        check(ctx, cap(level, input) == process.input, "сторона " + input + " — вход TFC");
        check(ctx, cap(level, Direction.UP) == process.input, "верх — вход TFC");
        MembraneElectrolyzerBlock block = (MembraneElectrolyzerBlock) level.getBlockState(ROT).getBlock();
        Vec3 plus = block.getNodePosition(level, ROT, level.getBlockState(ROT), ProcessPowerDevice.PLUS);
        Vec3 minus = block.getNodePosition(level, ROT, level.getBlockState(ROT), ProcessPowerDevice.MINUS);
        check(ctx, near(plus, expectedNode(facing, 0.4)) && near(minus, expectedNode(facing, 0.6)),
            "узлы EE повернулись вместе с блоком: PLUS " + plus + ", MINUS " + minus);
        return report(ctx);
    }

    /** Ключом повёрнутая машина без перестройки корпуса: вещества на месте, производство стоит. */
    private static int rotateWithWrench(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        MembraneElectrolyzerBlockEntity process = process(level);
        process.hydrogen.fill(new FluidStack(Fluids.WATER, 100),
            IFluidHandler.FluidAction.EXECUTE);
        BlockState state = level.getBlockState(ROT);
        BlockState rotated = state.getBlock() instanceof MembraneElectrolyzerBlock block
            ? block.getRotatedBlockState(state, Direction.UP) : state;
        level.setBlock(ROT, rotated, 3);
        check(ctx, level.getBlockEntity(ROT) == process, "блок-сущность сохранена при повороте");
        check(ctx, level.getBlockState(ROT).getValue(MembraneElectrolyzerBlock.FACING) == Direction.EAST,
            "ключ повернул блок с севера на восток");
        check(ctx, process.hydrogen.getFluidAmount() == 100, "содержимое выхода H₂ сохранено");
        return report(ctx);
    }

    private static int assertRotated(CommandContext<CommandSourceStack> ctx) {
        reset();
        ServerLevel level = ctx.getSource().getLevel();
        MembraneElectrolyzerBlockEntity process = process(level);
        check(ctx, !process.formed(), "корпус не совпадает с повёрнутым блоком: машина не работает");
        check(ctx, process.batches == 0, "партий не было");
        return report(ctx);
    }

    private static Vec3 expectedNode(Direction facing, double localX) {
        return switch (facing) {
            case NORTH -> new Vec3(localX, 0.5, 0.5);
            case EAST -> new Vec3(0.5, 0.5, localX);
            case SOUTH -> new Vec3(1 - localX, 0.5, 0.5);
            default -> new Vec3(0.5, 0.5, 1 - localX);
        };
    }

    private static boolean near(Vec3 a, Vec3 b) {
        return a.distanceTo(b) < 1e-6;
    }

    private static IFluidHandler cap(ServerLevel level, Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, ROT, side);
    }

    private static MembraneElectrolyzerBlockEntity process(ServerLevel level) {
        if (level.getBlockEntity(ROT) instanceof MembraneElectrolyzerBlockEntity process) return process;
        throw new IllegalStateException("На позиции M12 нет электролизёра");
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
