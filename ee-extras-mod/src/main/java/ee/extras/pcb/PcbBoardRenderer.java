package ee.extras.pcb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
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
        var painter = new Painter(poses.last(), vertices, texture, light, overlay);
        int layer = PcbTopology.level(topology) - 1;
        for (int cell = 0; cell < PcbTopology.CELLS; cell++) {
            if (PcbTopology.cut(topology, cell)) continue;
            int x = cell % PcbTopology.SIZE;
            int z = cell / PcbTopology.SIZE;
            float x0 = Math.max(x, 0.25f) / 16;
            float z0 = Math.max(z, 0.25f) / 16;
            float x1 = Math.min(x + 1, 15.75f) / 16;
            float z1 = Math.min(z + 1, 15.75f) / 16;
            int color = burnt ? 0xFF2C2928 : switch (PcbTopology.get(topology, layer, cell)) {
                case PcbTopology.TRACE -> 0xFFA16635;
                case PcbTopology.VIA -> 0xFF8AA19A;
                case PcbTopology.COMPONENT -> 0xFFADAD86;
                default -> 0xFF285742;
            };
            painter.top(x0, z0, x1, z1, 1f / 16, color);
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
