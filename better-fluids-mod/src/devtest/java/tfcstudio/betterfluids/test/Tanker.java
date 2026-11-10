package tfcstudio.betterfluids.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Танкеры для библиотеки схем: горизонтальная бочка из белой обшивки с рамками, внутри один бак Better Fluids.
 * Ось — восток (x), сечение — круг диаметра d в плоскости y/z, скруглённый половинками и четвертями
 * ({@link Round}), торцы — круг d − 2 из алюминиевой обшивки, снизу рама из промышленного железа.
 * Каждый танкер строится, сохраняется в run/tanker_<d>x<len>.nbt, ставится из файла заново и заливается водой.
 */
final class Tanker {
    private static final BlockPos ORIGIN = new BlockPos(0, 100, 40);
    private static final int[][] SIZES = {{5, 12}, {7, 16}, {9, 22}};

    private Tanker() {
    }

    static String buildAll(ServerLevel level) {
        List<String> out = new ArrayList<>();
        int z = 0;
        for (int[] size : SIZES) {
            out.add(build(level, ORIGIN.offset(0, 0, z), size[0], size[1]));
            z += size[0] + 4;
        }
        return String.join("; ", out);
    }

    static Block block(String id) {
        return Round.block(id);
    }

    private static String build(ServerLevel level, BlockPos at, int d, int len) {
        Round.PLACED.clear();
        var white = block("better_fluids:white_cladding").defaultBlockState();
        var ring = block("better_fluids:white_cladding_framed").defaultBlockState();
        var cap = block("better_fluids:aluminum_cladding_framed").defaultBlockState();
        var tank = block("better_fluids:iron_tank").defaultBlockState();
        var frame = block("create:industrial_iron_block").defaultBlockState();
        int[][] body = Round.masks(d);
        int[][] end = Round.masks(d - 2);
        int width = d;
        int height = d + 2;
        int mid = d / 2;
        clear(level, at, len, height, width);
        int sections = 0;
        for (int x = 0; x < len; x++) {
            boolean isEnd = x == 0 || x == len - 1;
            boolean framed = x == 1 || x == len - 2 || (x - 1) % 4 == 0;
            for (int z = mid - 1; z <= mid + 1; z++) {
                level.setBlock(at.offset(x, 0, z), frame, 2);
            }
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < d; k++) {
                    BlockPos pos = at.offset(x, j + 1, k);
                    int mask = isEnd ? Round.at(end, j - 1, k - 1) : body[j][k];
                    if (Round.partial(mask)) {
                        Round.place(level, pos, mask, false, isEnd ? "better_fluids:aluminum_cladding" : "better_fluids:white_cladding");
                    } else if (!Round.full(mask)) {
                        continue;
                    } else if (isEnd) {
                        level.setBlock(pos, cap, 2);
                    } else if (Round.interior(body, j, k)) {
                        level.setBlock(pos, tank, 2);
                        sections++;
                    } else {
                        level.setBlock(pos, framed ? ring : white, 2);
                    }
                }
            }
        }
        // Горловина сверху посередине: две трубы Create вверх от бака
        int lowest = 0;
        while (!Round.interior(body, lowest, mid)) {
            lowest++;
        }
        int top = d - 1;
        while (!Round.full(body[top][mid])) {
            top--;
        }
        var pipe = block("create:fluid_pipe").defaultBlockState();
        List<BlockPos> neck = List.of(at.offset(len / 2, top + 1, mid), at.offset(len / 2, top + 2, mid));
        for (BlockPos pos : neck) {
            level.setBlock(pos, pipe, 2);
        }
        for (BlockPos pos : neck) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(pipe, level, pos), 2);
        }
        // Слив сзади: жидкостный интерфейс в торце у нижнего ряда бака
        BlockPos tap = at.offset(0, lowest + 1, mid);
        level.setBlock(tap, block("better_fluids:fluid_tap").defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.WEST), 3);

        String name = "tanker_" + d + "x" + len + ".nbt";
        BlockPos copy = at.offset(len + 6, 0, 0);
        clear(level, copy, len, height, width);
        Round.drops(level, copy, len);
        saveAndPlace(level, at, new Vec3i(len, height, width), name, copy);
        TankTest.check(level.getBlockState(copy.offset(0, lowest + 1, mid)).is(block("better_fluids:fluid_tap")), name + ": интерфейс отвалился");
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, copy.offset(1, lowest + 1, mid), Direction.WEST);
        int rounded = Round.verify(level, at, copy, name);
        String dropped = Round.drops(level, copy, len);
        TankTest.check(dropped.endsWith(" всего 0"), name + ": выпали предметы " + dropped);
        int capacity = sections * 14_000;
        TankTest.check(port != null && port.getTankCapacity(0) == capacity,
            name + ": ёмкость " + (port == null ? "нет" : port.getTankCapacity(0)) + " вместо " + capacity);
        int filled = port.fill(new FluidStack(Fluids.WATER, capacity + 5_000), FluidAction.EXECUTE);
        TankTest.check(filled == capacity, name + ": залито " + filled + " из " + capacity);
        BlockState neckState = level.getBlockState(copy.offset(len / 2, top + 1, mid));
        TankTest.check(neckState.getValue(BlockStateProperties.DOWN) && neckState.getValue(BlockStateProperties.UP),
            name + ": горловина не соединена с баком: " + neckState);
        return name + " — " + sections + " секций, " + capacity / 1000 + " вёдер, копикатов " + rounded;
    }

    /**
     * Печать как у схематичной пушки Create: слой за слоем снизу вверх, BlockHelper.placeSchematicBlock с
     * «безопасными» данными блок-сущности (writeSafe, как SchematicPrinter в выживании). Источник — малый танкер,
     * построенный командой tankers. Сравнивает состояния и ёмкость бака.
     */
    static String print(ServerLevel level) {
        BlockPos from = ORIGIN;
        int d = SIZES[0][0];
        int len = SIZES[0][1];
        int height = d + 2;
        BlockPos to = ORIGIN.offset(len + 30, 0, 0);
        clear(level, to, len, height, d);
        Round.drops(level, to, len);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < len; x++) {
                for (int z = 0; z < d; z++) {
                    BlockState state = level.getBlockState(from.offset(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    var source = level.getBlockEntity(from.offset(x, y, z));
                    CompoundTag data = null;
                    if (source instanceof com.simibubi.create.api.schematic.nbt.PartialSafeNBT safe) {
                        data = new CompoundTag();
                        safe.writeSafe(data, level.registryAccess());
                    } else if (source != null) {
                        data = source.saveWithoutMetadata(level.registryAccess());
                    }
                    com.simibubi.create.foundation.utility.BlockHelper.placeSchematicBlock(level, state, to.offset(x, y, z),
                        new net.minecraft.world.item.ItemStack(state.getBlock()), data);
                }
            }
        }
        return "напечатано, сверка — printcheck";
    }

    /** Сверка напечатанного print с исходником (после паузы — тики прошли) */
    static String printCheck(ServerLevel level) {
        BlockPos from = ORIGIN;
        int d = SIZES[0][0];
        int len = SIZES[0][1];
        int height = d + 2;
        BlockPos to = ORIGIN.offset(len + 30, 0, 0);
        int wrong = 0;
        String example = "";
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < len; x++) {
                for (int z = 0; z < d; z++) {
                    BlockState want = level.getBlockState(from.offset(x, y, z));
                    BlockState got = level.getBlockState(to.offset(x, y, z));
                    if (want != got) {
                        wrong++;
                        example = want + " → " + got + " @" + x + "," + y + "," + z;
                    }
                }
            }
        }
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, to.offset(1, 2, d / 2), Direction.WEST);
        int capacity = port == null ? -1 : port.getTankCapacity(0);
        TankTest.check(wrong == 0, "после печати не совпало " + wrong + ", напр. " + example + "; ёмкость " + capacity);
        return "печать совпала, ёмкость " + capacity;
    }

    /** Сохраняет участок в run/<name> и ставит его из файла в copy */
    static void saveAndPlace(ServerLevel level, BlockPos at, Vec3i size, String name, BlockPos copy) {
        var template = new StructureTemplate();
        template.fillFromWorld(level, at, size, false, Blocks.AIR);
        var file = level.getServer().getServerDirectory().resolve(name);
        CompoundTag saved;
        try {
            NbtIo.writeCompressed(template.save(new CompoundTag()), file);
            saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        level.getStructureManager().readStructure(saved).placeInWorld(level, copy, copy, new StructurePlaceSettings(), level.random, 3);
    }

    static void clear(ServerLevel level, BlockPos at, int len, int height, int width) {
        for (int x = -1; x <= len; x++) {
            for (int y = 0; y < height + 2; y++) {
                for (int z = -1; z <= width; z++) {
                    level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }
}
