package ee.extras.pcb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

final class PcbBoardRenderer implements BlockEntityRenderer<PcbCircuitBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("block/white_concrete");

    @Override
    public void render(PcbCircuitBlockEntity board, float tick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!board.getBlockState().getValue(PcbCircuitBlock.CUSTOM)) return;
        var topology = board.topology();
        if (topology == null) return;
        boolean burnt = board.getBlockState().getValue(PcbCircuitBlock.BURNT);
        var texture = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WHITE);
        var vertices = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        Direction face = board.getBlockState().getValue(PcbCircuitBlock.FACING);
        if (face.getAxis().isHorizontal()) {
            poses.mulPose(Axis.YP.rotationDegrees(switch (face) {
                case EAST -> -90;
                case SOUTH -> 180;
                case WEST -> 90;
                default -> 0;
            }));
            poses.mulPose(Axis.XP.rotationDegrees(-90));
        } else if (face == Direction.DOWN) {
            poses.mulPose(Axis.XP.rotationDegrees(180));
        }
        poses.scale(0.75f, 1, 0.75f);
        poses.translate(-0.5, -0.5, -0.5);
        var painter = new Painter(poses.last(), vertices, texture, light, overlay);
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        int layer = PcbTopology.level(topology) - 1;
        int substrate = burnt ? 0xFF2C2928 : switch (layer) {
            case 1 -> 0xFF285742;
            case 2 -> 0xFF274756;
            default -> 0xFF615139;
        };
        for (int cell = 0; cell < PcbTopology.CELLS; cell++) {
            if (PcbTopology.cut(topology, cell)) continue;
            int x = cell % PcbTopology.SIZE;
            int z = cell / PcbTopology.SIZE;
            float x0 = x / 16f;
            float z0 = z / 16f;
            float x1 = (x + 1) / 16f;
            float z1 = (z + 1) / 16f;
            int color = burnt ? 0xFF2C2928 : switch (PcbTopology.get(topology, layer, cell)) {
                case PcbTopology.TRACE -> 0xFFA16635;
                case PcbTopology.VIA -> 0xFF8AA19A;
                default -> substrate;
            };
            painter.top(x0, z0, x1, z1, 1f / 16, substrate);
            if (color != substrate) {
                float cx = (x + 0.5f) / 16;
                float cz = (z + 0.5f) / 16;
                float half = 0.15f / 16;
                float surface = 1.005f / 16;
                painter.top(cx - half, cz - half, cx + half, cz + half, surface, color);
                if (x > 0 && joins(topology, definitions, layer, cell, cell - 1))
                    painter.top(x0, cz - half, cx - half, cz + half, surface, color);
                if (x < 15 && joins(topology, definitions, layer, cell, cell + 1))
                    painter.top(cx + half, cz - half, x1, cz + half, surface, color);
                if (z > 0 && joins(topology, definitions, layer, cell, cell - 16))
                    painter.top(cx - half, z0, cx + half, cz - half, surface, color);
                if (z < 15 && joins(topology, definitions, layer, cell, cell + 16))
                    painter.top(cx - half, cz + half, cx + half, z1, surface, color);
            }
            painter.bottom(x0, z0, x1, z1, burnt ? 0xFF252323 : 0xFF234837);
            int edge = burnt ? 0xFF312D2B : 0xFF48765B;
            if (x == 0 || PcbTopology.cut(topology, cell - 1))
                painter.side(Direction.WEST, x0, z1, x0, z0, 0, 1f / 16, edge);
            if (x == 15 || PcbTopology.cut(topology, cell + 1))
                painter.side(Direction.EAST, x1, z0, x1, z1, 0, 1f / 16, edge);
            if (z == 0 || PcbTopology.cut(topology, cell - 16))
                painter.side(Direction.NORTH, x0, z0, x1, z0, 0, 1f / 16, edge);
            if (z == 15 || PcbTopology.cut(topology, cell + 16))
                painter.side(Direction.SOUTH, x1, z1, x0, z1, 0, 1f / 16, edge);
            for (int partLayer = layer; partLayer >= 0; partLayer--) {
                int index = partLayer * PcbTopology.CELLS + cell;
                if (index < definitions.length && definitions[index] != 0) {
                    int type = PcbWiring.type(definitions[index]);
                    boolean indicatorLit = !burnt && (type == 4 || type == 13)
                            && board.indicatorLit(PcbWiring.componentOrdinal(definitions, index));
                    PcbPartModels.render(definitions[index], x, z, burnt, indicatorLit, board.hasCap(cell), board.controlActive(PcbWiring.componentOrdinal(definitions, index)), board.controlPosition(PcbWiring.componentOrdinal(definitions, index)), poses, vertices, light, overlay);
                    if (type == 13 && indicatorLit) {
                        var glow = new Painter(poses.last(), vertices, texture,
                                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay);
                        // Световое пятно шире корпуса, чтобы состояние читалось с обычной дистанции.
                        glow.top((x - 0.35f) / 16, (z - 0.35f) / 16,
                                (x + 1.35f) / 16, (z + 1.35f) / 16, 1.03f / 16, 0xFFEF351E);
                        glow.top((x + 0.17f) / 16, (z + 0.17f) / 16,
                                (x + 0.83f) / 16, (z + 0.83f) / 16, 1.46f / 16, 0xFFFFE4A3);
                    }
                    break;
                }
            }
            if (PcbTopology.get(topology, 0, cell) == PcbTopology.TERMINAL) {
                int pad = burnt ? 0xFF48413B : 0xFFC98A4E;
                float px0 = x / 16f;
                float pz0 = z / 16f;
                float px1 = (x + 1) / 16f;
                float pz1 = (z + 1) / 16f;
                painter.top(px0, pz0, px1, pz1, 2f / 16, pad);
                painter.side(Direction.WEST, px0, pz1, px0, pz0, 1f / 16, 2f / 16, pad);
                painter.side(Direction.EAST, px1, pz0, px1, pz1, 1f / 16, 2f / 16, pad);
                painter.side(Direction.NORTH, px0, pz0, px1, pz0, 1f / 16, 2f / 16, pad);
                painter.side(Direction.SOUTH, px1, pz1, px0, pz1, 1f / 16, 2f / 16, pad);
            }
        }
        poses.popPose();
    }

    private static boolean joins(CompoundTag topology, int[] definitions, int layer, int from, int to) {
        if (PcbTopology.cut(topology, to)) return false;
        int value = PcbTopology.get(topology, layer, to);
        if (PcbWiring.conductor(value)) return true;
        int index = PcbFootprint.owner(definitions, layer, to);
        if (index < 0) return false;
        int definition = definitions[index];
        int type = PcbWiring.type(definition);
        int pins = PcbModules.get(definition).pins();
        for (int pin = 0; pin < pins; pin++) {
            if (PcbWiring.pinCell(index, definition, pin) % PcbTopology.CELLS == from) return true;
        }
        return false;
    }

    private record Painter(PoseStack.Pose pose, VertexConsumer vertices, TextureAtlasSprite texture, int light,
                           int overlay) {
        void top(float x0, float z0, float x1, float z1, float y, int color) {
            vertex(Direction.UP, x0, y, z0, color);
            vertex(Direction.UP, x0, y, z1, color);
            vertex(Direction.UP, x1, y, z1, color);
            vertex(Direction.UP, x1, y, z0, color);
        }

        void bottom(float x0, float z0, float x1, float z1, int color) {
            vertex(Direction.DOWN, x0, 0, z0, color);
            vertex(Direction.DOWN, x1, 0, z0, color);
            vertex(Direction.DOWN, x1, 0, z1, color);
            vertex(Direction.DOWN, x0, 0, z1, color);
        }

        void side(Direction normal, float x0, float z0, float x1, float z1, float y0, float y1, int color) {
            vertex(normal, x0, y0, z0, color);
            vertex(normal, x0, y1, z0, color);
            vertex(normal, x1, y1, z1, color);
            vertex(normal, x1, y0, z1, color);
        }

        private void vertex(Direction normal, float x, float y, float z, int color) {
            vertices.addVertex(pose.pose(), x, y, z).setColor(color)
                    .setUv((texture.getU0() + texture.getU1()) / 2, (texture.getV0() + texture.getV1()) / 2)
                    .setOverlay(overlay).setLight(light).setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
        }
    }
}
