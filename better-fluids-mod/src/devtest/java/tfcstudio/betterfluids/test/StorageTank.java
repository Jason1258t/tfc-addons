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
 * Вертикальные резервуары как на нефтебазе: цилиндр диаметра d и высоты h из белой обшивки с поясами-рамками,
 * внутри один бак Better Fluids, снизу основание из промышленного железа на клетку шире, сверху плоская крыша с
 * ограждением из прутьев, лестница с юга, горловина — труба в центре крыши, внизу труба на восток и интерфейс
 * на севере. Ступеньки стен закрыты вертикальными скосами Copycats+ (если мод загружен). Сохраняется в
 * run/storage_tank_<d>x<h>.nbt и проверяется установкой из файла.
 */
final class StorageTank {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 140);
    private static final int[][] SIZES = {{9, 10}, {13, 14}, {17, 18}};

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

    private static String build(ServerLevel level, BlockPos at, int d, int h) {
        var white = Tanker.block("better_fluids:white_cladding").defaultBlockState();
        var ring = Tanker.block("better_fluids:white_cladding_framed").defaultBlockState();
        var hatch = Tanker.block("better_fluids:aluminum_cladding_framed").defaultBlockState();
        var tank = Tanker.block("better_fluids:iron_tank").defaultBlockState();
        var base = Tanker.block("create:industrial_iron_block").defaultBlockState();
        var pipe = Tanker.block("create:fluid_pipe").defaultBlockState();
        int size = d + 2;
        int height = h + 3;
        int c = d / 2;
        Tanker.clear(level, at.below(0), size, height, size);
        int sections = 0;
        int slopes = 0;
        // Основание: диск на клетку шире стены
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (Tanker.inside(size, x, z)) {
                    level.setBlock(at.offset(x, 0, z), base, 2);
                }
            }
        }
        // Стена, бак и крыша; клетка диска (i, k) стоит в (i + 1, k + 1)
        for (int y = 1; y <= h + 1; y++) {
            boolean roof = y == h + 1;
            boolean framed = y == 1 || y == h || (y - 1) % 4 == 0;
            for (int i = 0; i < d; i++) {
                for (int k = 0; k < d; k++) {
                    BlockPos pos = at.offset(i + 1, y, k + 1);
                    if (roof && Tanker.inside(d, i, k)) {
                        boolean centre = Math.abs(i - c) <= 1 && Math.abs(k - c) <= 1;
                        level.setBlock(pos, centre ? hatch : Tanker.interior(d, i, k) ? white : ring, 2);
                    } else if (Tanker.interior(d, i, k)) {
                        level.setBlock(pos, tank, 2);
                        sections++;
                    } else if (Tanker.inside(d, i, k)) {
                        level.setBlock(pos, framed ? ring : white, 2);
                    }
                    if (slope(level, pos, d, i, k)) {
                        slopes++;
                    }
                }
            }
        }
        // Ограждение крыши: прутья по краю диска, проход к лестнице с юга
        var bars = Blocks.IRON_BARS.defaultBlockState();
        List<BlockPos> rail = new ArrayList<>();
        for (int i = 0; i < d; i++) {
            for (int k = 0; k < d; k++) {
                if (Tanker.inside(d, i, k) && !Tanker.interior(d, i, k) && !(i == c && k == d - 1)) {
                    BlockPos pos = at.offset(i + 1, h + 2, k + 1);
                    level.setBlock(pos, bars, 2);
                    rail.add(pos);
                }
            }
        }
        for (BlockPos pos : rail) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(bars, level, pos), 2);
        }
        // Лестница с юга от основания до крыши
        var ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        for (int y = 1; y <= h + 1; y++) {
            level.setBlock(at.offset(c + 1, y, d + 1), ladder, 2);
        }
        // Горловина в центре крыши, труба внизу на восток, интерфейс внизу на севере
        List<BlockPos> pipes = List.of(at.offset(c + 1, h + 1, c + 1), at.offset(c + 1, h + 2, c + 1),
            at.offset(d, 1, c + 1), at.offset(d + 1, 1, c + 1));
        for (BlockPos pos : pipes) {
            level.setBlock(pos, pipe, 2);
        }
        for (BlockPos pos : pipes) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(pipe, level, pos), 2);
        }
        level.setBlock(at.offset(c + 1, 1, 1), Tanker.block("better_fluids:fluid_tap").defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH), 3);

        var template = new StructureTemplate();
        template.fillFromWorld(level, at, new Vec3i(size, height, size), false, Blocks.AIR);
        String name = "storage_tank_" + d + "x" + h + ".nbt";
        var file = level.getServer().getServerDirectory().resolve(name);
        CompoundTag saved;
        try {
            NbtIo.writeCompressed(template.save(new CompoundTag()), file);
            saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        // Установка из файла рядом (на юг) и проверки
        BlockPos copy = at.offset(0, 0, size + 6);
        Tanker.clear(level, copy, size, height, size);
        level.getStructureManager().readStructure(saved).placeInWorld(level, copy, copy, new StructurePlaceSettings(), level.random, 3);
        TankTest.check(level.getBlockState(copy.offset(c + 1, 1, 1)).is(Tanker.block("better_fluids:fluid_tap")),
            name + ": интерфейс отвалился");
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, copy.offset(c + 1, 1, 2), Direction.NORTH);
        int capacity = sections * 14_000;
        TankTest.check(port != null && port.getTankCapacity(0) == capacity,
            name + ": ёмкость " + (port == null ? "нет" : port.getTankCapacity(0)) + " вместо " + capacity);
        int filled = port.fill(new FluidStack(Fluids.WATER, capacity + 5_000), FluidAction.EXECUTE);
        TankTest.check(filled == capacity, name + ": залито " + filled + " из " + capacity);
        var neck = level.getBlockState(copy.offset(c + 1, h + 1, c + 1));
        TankTest.check(neck.getValue(BlockStateProperties.DOWN) && neck.getValue(BlockStateProperties.UP),
            name + ": горловина не соединена с баком: " + neck);
        var outlet = level.getBlockState(copy.offset(d, 1, c + 1));
        TankTest.check(outlet.getValue(BlockStateProperties.WEST) && outlet.getValue(BlockStateProperties.EAST),
            name + ": нижняя труба не соединена с баком: " + outlet);
        for (int y = 1; y <= h + 1; y++) {
            TankTest.check(level.getBlockState(copy.offset(c + 1, y, d + 1)).is(Blocks.LADDER), name + ": нет лестницы на " + y);
        }
        int checked = checkSlopes(level, copy, size, height, name);
        TankTest.check(Tanker.block("copycats:copycat_vertical_slope") == Blocks.AIR || checked == slopes,
            name + ": скосов после установки " + checked + " из " + slopes);
        return name + " — " + sections + " секций, " + capacity / 1000 + " вёдер, скосов " + slopes;
    }

    /** Точка у угла клетки в сторону (dx, dz), посередине по высоте */
    private static boolean filled(BlockState state, ServerLevel level, BlockPos pos, int dx, int dz) {
        return Tanker.contains(state.getShape(level, pos), 0.5 + 0.45 * dx, 0.5, 0.5 + 0.45 * dz);
    }

    /** Вертикальный скос в ступеньку стены: из четырёх сторон выбирается та, что заполняет угол к центру */
    private static boolean slope(ServerLevel level, BlockPos pos, int d, int i, int k) {
        Block block = Tanker.block("copycats:copycat_vertical_slope");
        if (block == Blocks.AIR || Tanker.inside(d, i, k)) {
            return false;
        }
        int c = d / 2;
        int di = i < c ? 1 : -1;
        int dk = k < c ? 1 : -1;
        if (!Tanker.inside(d, i + di, k) || !Tanker.inside(d, i, k + dk)) {
            return false;
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            if (filled(state, level, pos, di, dk) && !filled(state, level, pos, -di, -dk)) {
                level.setBlock(pos, state, 2);
                var tag = new CompoundTag();
                var item = new CompoundTag();
                item.putString("id", "better_fluids:white_cladding");
                item.putInt("count", 1);
                tag.put("Item", item);
                var material = new CompoundTag();
                material.putString("Name", "better_fluids:white_cladding");
                tag.put("Material", material);
                level.getBlockEntity(pos).loadWithComponents(tag, level.registryAccess());
                level.getBlockEntity(pos).setChanged();
                return true;
            }
        }
        throw new IllegalStateException("Нет стороны скоса для угла " + di + "," + dk);
    }

    /** После установки из файла: у каждого скоса материал на месте, а заполненный угол упирается в стену */
    private static int checkSlopes(ServerLevel level, BlockPos at, int size, int height, String name) {
        int checked = 0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < size; z++) {
                    BlockPos pos = at.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Tanker.block("copycats:copycat_vertical_slope"))) {
                        continue;
                    }
                    var nbt = level.getBlockEntity(pos).saveWithoutMetadata(level.registryAccess());
                    TankTest.check(nbt.getCompound("Material").getString("Name").equals("better_fluids:white_cladding"),
                        name + ": у скоса нет материала " + pos + " " + nbt);
                    int di = x - 1 < (size - 2) / 2 ? 1 : -1;
                    int dk = z - 1 < (size - 2) / 2 ? 1 : -1;
                    TankTest.check(filled(state, level, pos, di, dk) && !filled(state, level, pos, -di, -dk)
                        && !level.getBlockState(pos.offset(di, 0, 0)).isAir() && !level.getBlockState(pos.offset(0, 0, dk)).isAir(),
                        name + ": скос развёрнут не к стене " + pos + " " + state);
                    checked++;
                }
            }
        }
        return checked;
    }
}
