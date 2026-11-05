package tfcstudio.betterfluids;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

final class IronTankRenderer extends FluidTankRenderer {
    private static final float WALL = 0.0703125f;

    IronTankRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(FluidTankBlockEntity entity, float partialTicks, PoseStack pose,
                              MultiBufferSource buffer, int light, int overlay) {
        var tank = (IronTankEntity) entity;
        if (tank.legacy()) {
            super.renderSafe(tank, partialTicks, pose, buffer, light, overlay);
            return;
        }

        var state = tank.getBlockState();
        var fluid = tank.getTankInventory().getFluid();
        var level = tank.getFluidLevel();
        if (state.getValue(IronTankBlock.SHAPE) == IronTankBlock.Shape.PLAIN || fluid.isEmpty() || level == null) {
            return;
        }

        float bottom = state.getValue(IronTankBlock.BOTTOM) ? 0.25f : 0;
        float top = state.getValue(IronTankBlock.TOP) ? 0.75f : 1;
        float height = (top - bottom) * Mth.clamp(level.getValue(partialTicks), 0, 1);
        if (height < 1 / 512f) {
            return;
        }
        if (fluid.getFluid().getFluidType().isLighterThanAir()) {
            bottom = top - height;
        }

        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid,
            inset(tank, Direction.WEST), bottom, inset(tank, Direction.NORTH),
            1 - inset(tank, Direction.EAST), bottom + height, 1 - inset(tank, Direction.SOUTH),
            buffer, pose, light, false, true);
    }

    private static float inset(IronTankEntity tank, Direction direction) {
        return tank.getLevel() != null && IronTankEntity.connected(tank.getLevel(), tank.getBlockPos(), direction) ? 0 : WALL;
    }
}
