package ee.extras.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
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
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-panelFacing.toYRot()));
        float scale = slot.name().startsWith("QUARTER_") ? 0.25F : 0.32F;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(defaultDroppedStack(), ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, panel.getLevel(), 0);
        pose.popPose();
    }
}
