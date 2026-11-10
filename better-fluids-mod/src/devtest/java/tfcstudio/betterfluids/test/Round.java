package tfcstudio.betterfluids.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Круг из клеток с половинками и четвертями: клетка делится на четыре четверти в плоскости сечения, четверть
 * входит, если больше половины её площади внутри круга. Три или четыре четверти — целый блок, две соседние —
 * половинка (слэб Copycats+ по нужной оси), одна — четверть (ступенька Create вдоль оси x, вертикальная
 * ступенька Copycats+ вдоль y). Нужная ориентация подбирается по форме блока, без догадок о свойствах.
 * Бит маски: a·2 + b, a — первая координата сечения, b — z.
 */
final class Round {
    static final Map<BlockPos, int[]> PLACED = new HashMap<>();

    private Round() {
    }

    static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    /** Маски клеток круга диаметра d (в клетках) */
    static int[][] masks(int d) {
        int[][] out = new int[d][d];
        double c = d / 2.0;
        double r = d / 2.0;
        int n = 8;
        for (int j = 0; j < d; j++) {
            for (int k = 0; k < d; k++) {
                for (int a = 0; a < 2; a++) {
                    for (int b = 0; b < 2; b++) {
                        int in = 0;
                        for (int u = 0; u < n; u++) {
                            for (int v = 0; v < n; v++) {
                                double y = j + 0.5 * a + (u + 0.5) / (2 * n) - c;
                                double z = k + 0.5 * b + (v + 0.5) / (2 * n) - c;
                                in += y * y + z * z <= r * r ? 1 : 0;
                            }
                        }
                        if (in * 2 > n * n) {
                            out[j][k] |= 1 << (a * 2 + b);
                        }
                    }
                }
            }
        }
        return out;
    }

    static int at(int[][] m, int j, int k) {
        return j < 0 || k < 0 || j >= m.length || k >= m.length ? 0 : m[j][k];
    }

    static boolean full(int mask) {
        return Integer.bitCount(mask) >= 3 || mask == 0b1001 || mask == 0b0110;
    }

    static boolean interior(int[][] m, int j, int k) {
        return full(at(m, j, k)) && full(at(m, j + 1, k)) && full(at(m, j - 1, k))
            && full(at(m, j, k + 1)) && full(at(m, j, k - 1));
    }

    static boolean partial(int mask) {
        return mask != 0 && !full(mask);
    }

    private static boolean contains(VoxelShape shape, double x, double y, double z) {
        return shape.toAabbs().stream().anyMatch(box -> box.contains(x, y, z));
    }

    /** Форма совпадает с маской: занятые четверти заполнены по всей длине вдоль оси, свободные пусты */
    static boolean matches(BlockState state, ServerLevel level, BlockPos pos, int mask, boolean axisY) {
        VoxelShape shape = state.getShape(level, pos);
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                boolean want = (mask & 1 << (a * 2 + b)) != 0;
                for (double along : new double[]{0.05, 0.5, 0.95}) {
                    double p = 0.25 + 0.5 * a;
                    double q = 0.25 + 0.5 * b;
                    boolean has = axisY ? contains(shape, p, along, q) : contains(shape, along, p, q);
                    if (has != want) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Ставит половинку или четверть из материала; ось сечения — y (вертикальный бак) или x (лежачий) */
    static void place(ServerLevel level, BlockPos pos, int mask, boolean axisY, String material) {
        Block block = block(Integer.bitCount(mask) == 2 ? "copycats:copycat_slab"
            : axisY ? "copycats:copycat_vertical_step" : "create:copycat_step");
        TankTest.check(block != Blocks.AIR, "Нет копиката для маски " + mask + " (нужен Copycats+: WITH_COPYCATS=1)");
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                continue;
            }
            if (matches(state, level, pos, mask, axisY)) {
                level.setBlock(pos, state, 2);
                fill(level, pos, state, material);
                PLACED.put(pos.immutable(), new int[]{mask, axisY ? 1 : 0});
                return;
            }
        }
        throw new IllegalStateException("Нет формы " + block + " для маски " + mask + (axisY ? " по y" : " по x"));
    }

    /** Слэб как копикат целиком: обе части не нужны, ставится нижняя/верхняя по типу */
    static void slab(ServerLevel level, BlockPos pos, String material) {
        BlockState state = block("copycats:copycat_slab").defaultBlockState();
        level.setBlock(pos, state, 2);
        fill(level, pos, state, material);
    }

    private static void fill(ServerLevel level, BlockPos pos, BlockState state, String material) {
        var item = new CompoundTag();
        item.putString("id", material);
        item.putInt("count", 1);
        var name = new CompoundTag();
        name.putString("Name", material);
        var tag = new CompoundTag();
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            // Copycats+ MaterialItemStorage: часть по типу слэба
            var part = new CompoundTag();
            part.put("material", name);
            part.put("consumedItem", item);
            part.putBoolean("enableCT", true);
            var data = new CompoundTag();
            data.put(state.getValue(BlockStateProperties.SLAB_TYPE).getSerializedName(), part);
            tag.put("material_data", data);
        } else {
            tag.put("Item", item);
            tag.put("Material", name);
        }
        var entity = level.getBlockEntity(pos);
        TankTest.check(entity != null, "У копиката нет блок-сущности " + state);
        entity.loadWithComponents(tag, level.registryAccess());
        entity.setChanged();
    }

    /** Выпавшие предметы рядом с постройкой: копикат или лестница, сорвавшиеся при установке */
    static String drops(ServerLevel level, BlockPos from, int size) {
        var box = new net.minecraft.world.phys.AABB(from).inflate(size + 8);
        var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box);
        items.forEach(net.minecraft.world.entity.Entity::discard);
        return items.stream().map(i -> BuiltInRegistries.ITEM.getKey(i.getItem().getItem()) + "×" + i.getItem().getCount())
            .distinct().limit(8).toList().toString() + " всего " + items.size();
    }

    /** После установки из файла: каждая половинка/четверть на месте, форма та же, материал сохранён */
    static int verify(ServerLevel level, BlockPos from, BlockPos to, String name) {
        int checked = 0;
        for (var e : PLACED.entrySet()) {
            BlockPos rel = e.getKey().subtract(from);
            BlockPos pos = to.offset(rel);
            BlockState state = level.getBlockState(pos);
            TankTest.check(matches(state, level, pos, e.getValue()[0], e.getValue()[1] == 1),
                name + ": форма копиката не совпала " + pos + " " + state);
            var nbt = level.getBlockEntity(pos).saveWithoutMetadata(level.registryAccess()).toString();
            TankTest.check(nbt.contains("better_fluids:") || nbt.contains("create:industrial_iron_block"),
                name + ": у копиката нет материала " + pos + " " + nbt);
            checked++;
        }
        return checked;
    }
}
