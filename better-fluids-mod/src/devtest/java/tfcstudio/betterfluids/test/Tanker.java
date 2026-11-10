package tfcstudio.betterfluids.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Танкеры для библиотеки схем: горизонтальная бочка из белой обшивки с рамками, внутри один бак Better Fluids.
 * Ось — восток (x), поперечник — диск диаметра d в плоскости y/z, снизу рама из промышленного железа.
 * Ступеньки диска (пустые клетки с соседями внутрь по обеим осям) закрыты скосами Copycats+ из белой обшивки,
 * если мод загружен (WITH_COPYCATS=1). Каждый танкер строится, сохраняется в run/tanker_<d>x<len>.nbt, ставится из файла заново и заливается водой.
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
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    private static boolean disc(int d, int j, int k) {
        double c = (d - 1) / 2.0;
        double r = d / 2.0;
        return (j - c) * (j - c) + (k - c) * (k - c) <= r * r;
    }

    static boolean inside(int d, int j, int k) {
        return j >= 0 && k >= 0 && j < d && k < d && disc(d, j, k);
    }

    static boolean interior(int d, int j, int k) {
        return inside(d, j, k) && inside(d, j + 1, k) && inside(d, j - 1, k) && inside(d, j, k + 1) && inside(d, j, k - 1);
    }

    private static String build(ServerLevel level, BlockPos at, int d, int len) {
        var white = block("better_fluids:white_cladding").defaultBlockState();
        var ring = block("better_fluids:white_cladding_framed").defaultBlockState();
        var cap = block("better_fluids:aluminum_cladding_framed").defaultBlockState();
        var tank = block("better_fluids:iron_tank").defaultBlockState();
        var frame = block("create:industrial_iron_block").defaultBlockState();
        int width = d;
        int height = d + 3;
        int mid = d / 2;
        clear(level, at, len, height, width);
        int sections = 0;
        int slopes = 0;
        for (int x = 0; x < len; x++) {
            boolean end = x == 0 || x == len - 1;
            boolean framed = x == 1 || x == len - 2 || (x - 1) % 4 == 0;
            for (int z = mid - 1; z <= mid + 1; z++) {
                level.setBlock(at.offset(x, 0, z), frame, 2);
            }
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < d; k++) {
                    BlockPos pos = at.offset(x, j + 1, k);
                    if (end) {
                        if (inside(d - 2, j - 1, k - 1)) {
                            level.setBlock(pos, cap, 2);
                        }
                    } else if (interior(d, j, k)) {
                        level.setBlock(pos, tank, 2);
                        sections++;
                    } else if (inside(d, j, k)) {
                        level.setBlock(pos, framed ? ring : white, 2);
                    }
                    int disc = end ? d - 2 : d;
                    int jj = end ? j - 1 : j;
                    int kk = end ? k - 1 : k;
                    if (slope(level, pos, disc, jj, kk)) {
                        slopes++;
                    }
                }
            }
        }
        // Горловина сверху посередине: две трубы Create вверх от бака
        int lowest = 0;
        while (!interior(d, lowest, mid)) {
            lowest++;
        }
        var pipe = block("create:fluid_pipe").defaultBlockState();
        for (int y = d; y <= d + 1; y++) {
            level.setBlock(at.offset(len / 2, y, mid), pipe, 2);
        }
        for (int y = d; y <= d + 1; y++) {
            BlockPos pos = at.offset(len / 2, y, mid);
            level.setBlock(pos, Block.updateFromNeighbourShapes(pipe, level, pos), 2);
        }
        // Слив сзади: жидкостный интерфейс в торце у нижнего ряда бака
        BlockPos tap = at.offset(0, lowest + 1, mid);
        level.setBlock(tap, block("better_fluids:fluid_tap").defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.WEST), 3);
        for (int x = 0; x < len; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < width; z++) {
                    BlockPos pos = at.offset(x, y, z);
                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                }
            }
        }

        var template = new StructureTemplate();
        template.fillFromWorld(level, at, new net.minecraft.core.Vec3i(len, height, width), false, Blocks.AIR);
        String name = "tanker_" + d + "x" + len + ".nbt";
        try {
            NbtIo.writeCompressed(template.save(new CompoundTag()), level.getServer().getServerDirectory().resolve(name));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        // Повторная установка из файла рядом: бак единый, интерфейс держится, жидкость заливается в полную ёмкость
        BlockPos copy = at.offset(len + 6, 0, 0);
        clear(level, copy, len, height, width);
        CompoundTag saved;
        try {
            saved = NbtIo.readCompressed(level.getServer().getServerDirectory().resolve(name), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        var loaded = level.getStructureManager().readStructure(saved);
        loaded.placeInWorld(level, copy, copy, new StructurePlaceSettings(), level.random, 3);
        BlockPos copyTap = copy.offset(0, lowest + 1, mid);
        TankTest.check(level.getBlockState(copyTap).is(block("better_fluids:fluid_tap")), name + ": интерфейс отвалился");
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, copyTap.east(), Direction.WEST);
        checkSlopes(level, copy, len, height, width, name);
        int capacity = sections * 14_000;
        TankTest.check(port != null && port.getTankCapacity(0) == capacity,
            name + ": ёмкость " + (port == null ? "нет" : port.getTankCapacity(0)) + " вместо " + capacity);
        int filled = port.fill(new FluidStack(Fluids.WATER, capacity + 5_000), FluidAction.EXECUTE);
        TankTest.check(filled == capacity, name + ": залито " + filled + " из " + capacity);
        var neck = level.getBlockState(copy.offset(len / 2, d, mid));
        TankTest.check(neck.getValue(BlockStateProperties.DOWN) && neck.getValue(BlockStateProperties.UP),
            name + ": горловина не соединена с баком: " + neck);
        try {
            TankTest.check(Files.size(level.getServer().getServerDirectory().resolve(name)) > 0, name + ": пустой файл");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return name + " — " + sections + " секций, " + capacity / 1000 + " вёдер, скосов " + slopes;
    }

    /** Скос в ступеньку диска: высокая сторона к центру по z, половина — к центру по y (facing=south — выше на юге) */
    private static boolean slope(ServerLevel level, BlockPos pos, int d, int j, int k) {
        Block block = block("copycats:copycat_slope");
        if (block == Blocks.AIR || inside(d, j, k)) {
            return false;
        }
        int c = d / 2;
        int dj = j < c ? 1 : -1;
        int dk = k < c ? 1 : -1;
        if (!inside(d, j + dj, k) || !inside(d, j, k + dk)) {
            return false;
        }
        BlockState state = block.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, dk > 0 ? Direction.SOUTH : Direction.NORTH)
            .setValue(BlockStateProperties.HALF, dj > 0 ? net.minecraft.world.level.block.state.properties.Half.BOTTOM
                : net.minecraft.world.level.block.state.properties.Half.TOP);
        level.setBlock(pos, state, 2);
        var entity = level.getBlockEntity(pos);
        var tag = new CompoundTag();
        var item = new CompoundTag();
        item.putString("id", "better_fluids:white_cladding");
        item.putInt("count", 1);
        tag.put("Item", item);
        var material = new CompoundTag();
        material.putString("Name", "better_fluids:white_cladding");
        tag.put("Material", material);
        entity.loadWithComponents(tag, level.registryAccess());
        entity.setChanged();
        return true;
    }

    /** Проверка скоса после установки из файла: материал на месте, форма заполняет угол к центру */
    private static void checkSlopes(ServerLevel level, BlockPos at, int len, int height, int width, String name) {
        int checked = 0;
        for (int x = 0; x < len; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < width; z++) {
                    BlockPos pos = at.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(block("copycats:copycat_slope"))) {
                        continue;
                    }
                    var nbt = level.getBlockEntity(pos).saveWithoutMetadata(level.registryAccess());
                    TankTest.check(nbt.getString("Material").contains("white_cladding")
                        || nbt.getCompound("Material").getString("Name").equals("better_fluids:white_cladding"),
                        name + ": у скоса нет материала " + pos + " " + nbt);
                    boolean south = state.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH;
                    boolean bottom = state.getValue(BlockStateProperties.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM;
                    // Угол к центру — заполнен, противоположный — пуст
                    double fz = south ? 0.95 : 0.05;
                    double fy = bottom ? 0.05 : 0.95;
                    var shape = state.getShape(level, pos);
                    TankTest.check(contains(shape, 0.5, fy, fz) && !contains(shape, 0.5, 1 - fy, 1 - fz),
                        name + ": скос развёрнут не к центру " + pos + " " + state);
                    TankTest.check(level.getBlockState(pos.relative(south ? Direction.SOUTH : Direction.NORTH)).isSolidRender(level, pos)
                        || !level.getBlockState(pos.relative(south ? Direction.SOUTH : Direction.NORTH)).isAir(),
                        name + ": высокая сторона скоса смотрит в пустоту " + pos);
                    checked++;
                }
            }
        }
        TankTest.check(block("copycats:copycat_slope") == Blocks.AIR || checked > 0, name + ": скосов нет");
    }

    static boolean contains(net.minecraft.world.phys.shapes.VoxelShape shape, double x, double y, double z) {
        return shape.toAabbs().stream().anyMatch(box -> box.contains(x, y, z));
    }

    static void clear(ServerLevel level, BlockPos at, int len, int height, int width) {
        for (int x = -1; x <= len; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = -1; z <= width; z++) {
                    level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }
}
