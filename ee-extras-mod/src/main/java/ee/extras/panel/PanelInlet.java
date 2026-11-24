package ee.extras.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
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
    public void render(
            ElectricalPanelBlockEntity panel,
            float partialTicks,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        var anchor = PanelInletGeometry.anchor(slot);
        var center =
                net.createmod.catnip.math.VecHelper.rotateCentered(
                        anchor, 180 - panelFacing.toYRot(), Direction.Axis.Y);
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - panelFacing.toYRot()));
        // Внешняя сторона ввода прижата к верхней/нижней стенке, парные клеммы — внутрь.
        if (!PanelInletGeometry.upper(slot))
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
        // У исходной модели основание лежит в XZ, парные клеммы обращены к -Z.
        // После поворота нижний ввод смотрит клеммами вверх, верхний — вниз, внутрь щитка.
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
        pose.scale(0.5F, 0.5F, 0.5F);
        pose.translate(-0.5, 0, -9.5 / 16);
        var state = PanelInletGeometry.modelState();
        Minecraft.getInstance()
                .getBlockRenderer()
                .renderSingleBlock(state, pose, buffers, light, overlay);
        pose.popPose();
    }
}
