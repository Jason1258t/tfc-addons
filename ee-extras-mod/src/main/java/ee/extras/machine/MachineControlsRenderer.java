package ee.extras.machine;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;

final class MachineControlsRenderer {
    static void render(MotorMachine machine, float partialTicks, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        var controls = machine.drive().controls;
        controls.tick();
        var proxy = (ElectricalPanelBlockEntity) CEEBlocks.ELECTRICAL_PANEL.get().newBlockEntity(controls.owner.getBlockPos(),
                CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        proxy.setLevel(controls.owner.getLevel());
        for (int i = 0; i < 2; i++) {
            var module = controls.modules[i];
            if (module == null) continue;
            pose.pushPose();
            pose.translate(.5, 0, .5);
            pose.mulPose(Axis.YP.rotationDegrees(-controls.faces[i].toYRot()));
            pose.translate((i == 0 ? .25 : .75) - .5, .8, .505);
            pose.scale(.45f, .45f, .45f);
            // У щитковых моделей EE задняя плоскость z=14/16, центр x=5/16; лицевая сторона смотрит на -Z.
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(-5.0 / 16, -.5, -14.0 / 16);
            module.tickClient(proxy);
            module.render(proxy, partialTicks, pose, buffer, light, overlay);
            pose.popPose();
        }
    }
    private MachineControlsRenderer() {}
}
