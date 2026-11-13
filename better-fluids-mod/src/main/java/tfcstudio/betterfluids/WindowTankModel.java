package tfcstudio.betterfluids;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Стенки секции с окном. Стойка ставится только там, где стенка бака кончается; на стыке с соседней
 * секцией с окном вместо стойки идёт стекло, и окна соседних секций сливаются в одну полосу.
 * Крышку и дно даёт модель Create, соединённые текстуры — обёртка Create поверх этой модели.
 */
final class WindowTankModel extends BakedModelWrapperWithData {
    private static final ModelProperty<Integer> GLASS_ENDS = new ModelProperty<>();
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final ResourceLocation SIDE = ResourceLocation.fromNamespaceAndPath(BetterFluids.ID, "block/iron_tank");
    private static final ResourceLocation GLASS =
        ResourceLocation.fromNamespaceAndPath(BetterFluids.ID, "block/iron_tank_window_wide");

    // Чанки собираются в нескольких потоках.
    private final Map<Integer, List<BakedQuad>> walls = new ConcurrentHashMap<>();

    WindowTankModel(BakedModel original) {
        super(original);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, ModelData blockEntityData) {
        if (!windowed(state)) {
            return builder;
        }
        int ends = 0;
        for (Direction face : Direction.Plane.HORIZONTAL) {
            if (glass(world, pos, state, face, face.getCounterClockWise())) {
                ends |= 1 << face.get2DDataValue() * 2;
            }
            if (glass(world, pos, state, face, face.getClockWise())) {
                ends |= 2 << face.get2DDataValue() * 2;
            }
        }
        return builder.with(GLASS_ENDS, ends);
    }

    /** Стенка грани face продолжается в сторону end стеклом соседней секции. */
    private static boolean glass(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction face, Direction end) {
        BlockPos next = pos.relative(end);
        BlockState other = world.getBlockState(next);
        return IronTankEntity.shellConnected(world, pos, end) && windowed(other)
            && other.getValue(FluidTankBlock.TOP) == state.getValue(FluidTankBlock.TOP)
            && other.getValue(FluidTankBlock.BOTTOM) == state.getValue(FluidTankBlock.BOTTOM)
            && !IronTankEntity.shellConnected(world, next, face);
    }

    private static boolean windowed(BlockState state) {
        return state.is(BetterFluids.TANK.get()) && state.getValue(FluidTankBlock.SHAPE) != FluidTankBlock.Shape.PLAIN;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                    RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        if (!windowed(state) || !data.has(GLASS_ENDS)) {
            return quads;
        }
        boolean top = state.getValue(FluidTankBlock.TOP);
        boolean bottom = state.getValue(FluidTankBlock.BOTTOM);
        float wallBottom = bottom ? 0.25f : 0;
        float wallTop = top ? 0.75f : 1;
        // От модели Create остаются крышка и дно, стенки с окном строятся заново.
        var result = new ArrayList<BakedQuad>();
        for (BakedQuad quad : quads) {
            float min = 1;
            float max = 0;
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int i = 0; i < 4; i++) {
                float y = Float.intBitsToFloat(vertices[i * stride + 1]);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
            if (min >= wallTop - 1e-4f || max <= wallBottom + 1e-4f) {
                result.add(quad);
            }
        }
        if (side != null && side.getAxis().isHorizontal()) {
            int ends = data.get(GLASS_ENDS) >> side.get2DDataValue() * 2 & 3;
            int key = side.get2DDataValue() << 4 | (top ? 8 : 0) | (bottom ? 4 : 0) | ends;
            result.addAll(walls.computeIfAbsent(key, k -> wall(side, top, bottom, (ends & 1) != 0, (ends & 2) != 0)));
        }
        return result;
    }

    /**
     * Стенка грани face в координатах северной грани, повёрнутая на место: «запад» — сторона
     * face.getCounterClockWise(), «восток» — face.getClockWise().
     */
    private static List<BakedQuad> wall(Direction face, boolean top, boolean bottom, boolean westGlass, boolean eastGlass) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite side = atlas.getSprite(SIDE);
        TextureAtlasSprite glass = atlas.getSprite(GLASS);
        var rotation = BlockModelRotation.by(0, (int) face.toYRot() + 180);
        float y0 = bottom ? 4 : 0;
        float y1 = top ? 12 : 16;
        var quads = new ArrayList<BakedQuad>();
        if (!westGlass) {
            box(quads, side, rotation, 0, y0, 0, 4, y1, 1, Direction.NORTH, Direction.SOUTH, Direction.EAST);
        }
        if (!eastGlass) {
            box(quads, side, rotation, 12, y0, 0, 16, y1, 1, Direction.NORTH, Direction.SOUTH, Direction.WEST);
        }
        float x0 = westGlass ? 0 : 4;
        float x1 = eastGlass ? 16 : 12;
        // Девятичастная нарезка: рамка стекла только у стоек, крышки и дна, середина растягивается.
        var columns = new ArrayList<float[]>();
        float inner0 = westGlass ? x0 : x0 + 2;
        float inner1 = eastGlass ? x1 : x1 - 2;
        if (!westGlass) {
            columns.add(new float[] {x0, inner0, 14, 16});
        }
        columns.add(new float[] {inner0, inner1, 2, 14});
        if (!eastGlass) {
            columns.add(new float[] {inner1, x1, 0, 2});
        }
        var rows = new ArrayList<float[]>();
        float inner2 = bottom ? y0 + 3 : y0;
        float inner3 = top ? y1 - 2 : y1;
        if (bottom) {
            rows.add(new float[] {y0, inner2, 13, 16});
        }
        rows.add(new float[] {inner2, inner3, 2, 13});
        if (top) {
            rows.add(new float[] {inner3, y1, 0, 2});
        }
        for (float[] column : columns) {
            for (float[] row : rows) {
                var from = new Vector3f(column[0], row[0], 0.95f);
                var to = new Vector3f(column[1], row[1], 0.95f);
                // Снаружи (север) u растёт к меньшему x, изнутри — наоборот.
                quads.add(quad(from, to, glass, rotation, Direction.NORTH,
                    new float[] {column[2], row[2], column[3], row[3]}));
                quads.add(quad(from, to, glass, rotation, Direction.SOUTH,
                    new float[] {column[3], row[2], column[2], row[3]}));
            }
        }
        return quads;
    }

    private static void box(List<BakedQuad> quads, TextureAtlasSprite sprite, BlockModelRotation rotation,
                            float x0, float y0, float z0, float x1, float y1, float z1, Direction... faces) {
        var from = new Vector3f(x0, y0, z0);
        var to = new Vector3f(x1, y1, z1);
        for (Direction direction : faces) {
            quads.add(quad(from, to, sprite, rotation, direction, uv(direction, from, to)));
        }
    }

    /** Развёртка по умолчанию, как у элементов моделей Minecraft без "uv". */
    private static float[] uv(Direction direction, Vector3f from, Vector3f to) {
        return switch (direction) {
            case NORTH -> new float[] {16 - to.x, 16 - to.y, 16 - from.x, 16 - from.y};
            case SOUTH -> new float[] {from.x, 16 - to.y, to.x, 16 - from.y};
            case WEST -> new float[] {from.z, 16 - to.y, to.z, 16 - from.y};
            case EAST -> new float[] {16 - to.z, 16 - to.y, 16 - from.z, 16 - from.y};
            case UP -> new float[] {from.x, from.z, to.x, to.z};
            case DOWN -> new float[] {from.x, 16 - to.z, to.x, 16 - from.z};
        };
    }

    private static BakedQuad quad(Vector3f from, Vector3f to, TextureAtlasSprite sprite, BlockModelRotation rotation,
                                  Direction direction, float[] uv) {
        var face = new BlockElementFace(null, -1, "", new BlockFaceUV(uv, 0));
        return BAKERY.bakeQuad(new Vector3f(from), new Vector3f(to), face, sprite, direction, rotation, null, true);
    }
}
