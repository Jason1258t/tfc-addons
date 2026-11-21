package ee.extras.panel;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

final class PanelInlet extends PanelAttachment {
    PanelInlet(PanelAttachmentType type) {
        super(type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {}

    @Override
    public void postTick(SimulationResults results) {}

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(ElectricalPanelBlockEntity panel, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var center = getCenter().subtract(pos.getX(), pos.getY(), pos.getZ());
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - panelFacing.toYRot()));
        // Задняя стенка щитка — z=16/16; основание чуть впереди неё, без масштаба предмета FIXED.
        pose.translate(0, 0, 3.9 / 16);
        if (slot.center.y <= 0.5) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
        // У исходной модели основание лежит в XZ, парные клеммы обращены к -Z.
        // После поворота нижний ввод смотрит клеммами вверх, верхний — вниз, внутрь щитка.
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
        pose.scale(0.5F, 0.5F, 0.5F);
        pose.translate(-0.5, 0, -9.5 / 16);
        var state = CEEBlocks.DUPLEX_WIRE_TERMINATION.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.UP);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay);
        pose.popPose();
    }
}
