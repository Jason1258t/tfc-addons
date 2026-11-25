package tfc.destroy.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import tfc.destroy.extras.etching.EtchingBathBlock;
import tfc.destroy.extras.etching.EtchingBathBlockEntity;

/** Раствор и четыре вертикальные платы: состояние видно без открытия интерфейса. */
public final class EtchingBathRenderer implements BlockEntityRenderer<EtchingBathBlockEntity> {
    public EtchingBathRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EtchingBathBlockEntity bath, float partialTicks, PoseStack pose, MultiBufferSource buffer,
        int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-bath.getBlockState().getValue(EtchingBathBlock.FACING).toYRot()));
        pose.translate(-0.5, 0, -0.5);
        if (!bath.input.isEmpty()) {
            float height = 0.125f + 0.75f * bath.input.getFluidAmount() / bath.input.getCapacity();
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(bath.input.getFluid(),
                0.125f, 0.125f, 0.125f, 0.875f, height, 0.875f, buffer, pose, light, false, false);
        }
        for (int i = 0; i < EtchingBathBlockEntity.BOARD_SLOTS.length; i++) {
            var stack = bath.items.getStackInSlot(EtchingBathBlockEntity.BOARD_SLOTS[i]);
            if (stack.isEmpty()) continue;
            pose.pushPose();
            pose.translate(0.5, 0.8, 0.23 + i * 0.18);
            pose.scale(0.65f, 0.65f, 0.65f);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                light, overlay, pose, buffer, bath.getLevel(), i);
            pose.popPose();
        }
        pose.popPose();
    }
}
