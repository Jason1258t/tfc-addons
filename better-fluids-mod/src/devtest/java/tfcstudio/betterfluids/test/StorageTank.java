package tfcstudio.betterfluids.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Вертикальные резервуары как на нефтебазе — широкие и низкие (высота стены около 0,6 диаметра): цилиндр из
 * белой обшивки с поясами-рамками каждые 3 блока, скруглённый половинками и четвертями ({@link Round}), внутри
 * один бак Better Fluids. Основание из промышленного железа на клетку шире, крыша — плоский диск и пологий
 * конус из нижних полублоков с алюминиевым люком, по краю ограждение из прутьев. Лестница с юга, горловина в
 * центре крыши, внизу труба на восток и жидкостный интерфейс на севере. Сохраняется в
 * run/storage_tank_<d>x<h>.nbt и проверяется установкой из файла.
 */
final class StorageTank {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 140);
    private static final int[][] SIZES = {{11, 7}, {15, 9}, {21, 12}};
    private static final String WHITE = "better_fluids:white_cladding";

    private StorageTank() {
    }

    static String buildAll(ServerLevel level) {
        List<String> out = new ArrayList<>();
        int x = 0;
        for (int[] size : SIZES) {
            out.add(build(level, ORIGIN.offset(x, 0, 0), size[0], size[1]));
            x += size[0] + 6;
        }
        return String.join("; ", out);
    }

    /** Край крыши по восьми соседям: кольцо прутьев без разрывов на диагональных ступеньках */
    private static boolean edge(int[][] m, int i, int k) {
        for (int di = -1; di <= 1; di++) {
            for (int dk = -1; dk <= 1; dk++) {
                if (!Round.full(Round.at(m, i + di, k + dk))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String build(ServerLevel level, BlockPos at, int d, int h) {
        Round.PLACED.clear();
        var white = Round.block(WHITE).defaultBlockState();
        var ring = Round.block("better_fluids:white_cladding_framed").defaultBlockState();
        var hatch = Round.block("better_fluids:aluminum_cladding_framed").defaultBlockState();
        var tank = Round.block("better_fluids:iron_tank").defaultBlockState();
        var base = Round.block("create:industrial_iron_block").defaultBlockState();
        var pipe = Round.block("create:fluid_pipe").defaultBlockState();
        int size = d + 2;
        int height = h + 4;
        int c = d / 2;
        int[][] wall = Round.masks(d);
        int[][] foot = Round.masks(size);
        int[][] cone = Round.masks(d - 6);
        Tanker.clear(level, at, size, height, size);
        int sections = 0;
        // Основание: круг на клетку шире стены; клетка круга стены (i, k) стоит в (i + 1, k + 1)
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = at.offset(x, 0, z);
                if (Round.full(foot[x][z])) {
                    level.setBlock(pos, base, 2);
                } else if (Round.partial(foot[x][z])) {
                    Round.place(level, pos, foot[x][z], true, "create:industrial_iron_block");
                }
            }
        }
        // Стена, бак и плоская крыша
        for (int y = 1; y <= h + 1; y++) {
            boolean roof = y == h + 1;
            boolean framed = y == 1 || y == h || (y - 1) % 3 == 0;
            for (int i = 0; i < d; i++) {
                for (int k = 0; k < d; k++) {
                    BlockPos pos = at.offset(i + 1, y, k + 1);
                    int mask = wall[i][k];
                    if (Round.partial(mask)) {
                        Round.place(level, pos, mask, true, WHITE);
                    } else if (!Round.full(mask)) {
                        continue;
                    } else if (roof) {
                        level.setBlock(pos, Round.interior(wall, i, k) ? white : ring, 2);
                    } else if (Round.interior(wall, i, k)) {
                        level.setBlock(pos, tank, 2);
                        sections++;
                    } else {
                        level.setBlock(pos, framed ? ring : white, 2);
                    }
                }
            }
        }
        // Пологий конус: нижние полублоки по кругу d − 6, в центре люк 3×3
        for (int i = 0; i < d - 6; i++) {
            for (int k = 0; k < d - 6; k++) {
                BlockPos pos = at.offset(i + 4, h + 2, k + 4);
                if (Math.abs(i + 3 - c) <= 1 && Math.abs(k + 3 - c) <= 1) {
                    level.setBlock(pos, hatch, 2);
                } else if (Round.full(cone[i][k])) {
                    Round.slab(level, pos, WHITE);
                }
            }
        }
        // Лестница с юга по крайней целой клетке среднего столбца, проход в ограждении над ней
        int south = d - 1;
        while (!Round.full(wall[c][south])) {
            south--;
        }
        TankTest.check(Round.at(wall, c, south + 1) == 0, "У лестницы стоит копикат");
        // Ограждение: прутья над краевыми целыми клетками крыши
        var bars = Blocks.IRON_BARS.defaultBlockState();
        List<BlockPos> rail = new ArrayList<>();
        for (int i = 0; i < d; i++) {
            for (int k = 0; k < d; k++) {
                if (Round.full(wall[i][k]) && edge(wall, i, k) && !(i == c && k == south)) {
                    BlockPos pos = at.offset(i + 1, h + 2, k + 1);
                    level.setBlock(pos, bars, 2);
                    rail.add(pos);
                }
            }
        }
        for (BlockPos pos : rail) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(bars, level, pos), 2);
        }
        var ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        for (int y = 1; y <= h + 1; y++) {
            level.setBlock(at.offset(c + 1, y, south + 2), ladder, 2);
        }
        // Горловина в центре крыши через люк, труба внизу на восток, интерфейс внизу на севере
        int east = d - 1;
        while (!Round.full(wall[east][c])) {
            east--;
        }
        int north = 0;
        while (!Round.full(wall[c][north])) {
            north++;
        }
        TankTest.check(Round.interior(wall, east - 1, c) && Round.interior(wall, c, north + 1), "Выводы не у бака");
        List<BlockPos> pipes = List.of(at.offset(c + 1, h + 1, c + 1), at.offset(c + 1, h + 2, c + 1), at.offset(c + 1, h + 3, c + 1),
            at.offset(east + 1, 1, c + 1), at.offset(east + 2, 1, c + 1));
        for (BlockPos pos : pipes) {
            level.setBlock(pos, pipe, 2);
            Round.PLACED.remove(pos);
        }
        for (BlockPos pos : pipes) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(pipe, level, pos), 2);
        }
        BlockPos tap = at.offset(c + 1, 1, north + 1);
        level.setBlock(tap, Round.block("better_fluids:fluid_tap").defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH), 3);

        String name = "storage_tank_" + d + "x" + h + ".nbt";
        BlockPos copy = at.offset(0, 0, size + 6);
        Tanker.clear(level, copy, size, height, size);
        Round.drops(level, copy, size);
        Tanker.saveAndPlace(level, at, new Vec3i(size, height, size), name, copy);
        TankTest.check(level.getBlockState(copy.offset(c + 1, 1, north + 1)).is(Round.block("better_fluids:fluid_tap")),
            name + ": интерфейс отвалился");
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, copy.offset(c + 1, 1, north + 2), Direction.NORTH);
        String dropped = Round.drops(level, copy, size);
        TankTest.check(dropped.endsWith(" всего 0"), name + ": выпали предметы " + dropped);
        int capacity = sections * 14_000;
        TankTest.check(port != null && port.getTankCapacity(0) == capacity,
            name + ": ёмкость " + (port == null ? "нет" : port.getTankCapacity(0)) + " вместо " + capacity);
        int filled = port.fill(new FluidStack(Fluids.WATER, capacity + 5_000), FluidAction.EXECUTE);
        TankTest.check(filled == capacity, name + ": залито " + filled + " из " + capacity);
        var neck = level.getBlockState(copy.offset(c + 1, h + 1, c + 1));
        TankTest.check(neck.getValue(BlockStateProperties.DOWN) && neck.getValue(BlockStateProperties.UP),
            name + ": горловина не соединена с баком: " + neck);
        var outlet = level.getBlockState(copy.offset(east + 1, 1, c + 1));
        TankTest.check(outlet.getValue(BlockStateProperties.WEST) && outlet.getValue(BlockStateProperties.EAST),
            name + ": нижняя труба не соединена с баком: " + outlet);
        for (int y = 1; y <= h + 1; y++) {
            TankTest.check(level.getBlockState(copy.offset(c + 1, y, south + 2)).is(Blocks.LADDER), name + ": нет лестницы на " + y);
        }
        int rounded = Round.verify(level, at, copy, name);
        return name + " — " + sections + " секций, " + capacity / 1000 + " вёдер, копикатов " + rounded;
    }
}
