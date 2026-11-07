package ee.extras.pcb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Модели Create: Power Grid, адаптированные к монтажной площади модулей. См. NOTICE. */
final class PcbPartModels {
    private static final String[] NAMES = java.util.Arrays.stream(PcbModules.ALL).map(PcbModules.Module::model).toArray(String[]::new);

    private PcbPartModels() {}

    private static ModelResourceLocation id(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("ee_extras", "block/pcb_part/" + name));
    }

    static void register(ModelEvent.RegisterAdditional event) {
        for (String name : NAMES) event.register(id(name));
        event.register(id("selector_left"));
        event.register(id("selector_right"));
        event.register(id("latching_relay_on"));
        event.register(id("comparator_on"));
        event.register(id("slider_handle"));
        event.register(id("timer_hand"));
        event.register(id("led_on"));
        event.register(id("light_glow"));
        event.register(id("lamp_glow"));
        event.register(id("light_segment_glow"));
        event.register(id("led_panel_on"));
        event.register(id("led_strip_segment_on"));
        event.register(id("button_on"));
        event.register(id("switch_on"));
        event.register(id("button_cap"));
        event.register(id("switch_cap"));
        event.register(id("button_cap_on"));
        event.register(id("switch_cap_on"));
    }

    static void render(int definition, int x, int z, boolean burnt, long sections, boolean cap, boolean active, int position, PoseStack poses,
                       VertexConsumer vertices, int light, int overlay) {
        int type = PcbWiring.type(definition);
        if (definition <= 0 || type >= NAMES.length) return;
        boolean indicatorLit = sections != 0;
        var module = PcbModules.get(definition);
        var minecraft = Minecraft.getInstance();
        String name = module.kind() == PcbCircuitDevice.Kind.THREE_POSITION_SWITCH
                ? NAMES[type] + (position < 34 ? "_left" : position > 66 ? "_right" : "")
                : (module.kind() == PcbCircuitDevice.Kind.LATCHING_RELAY || module.kind() == PcbCircuitDevice.Kind.COMPARATOR) && active ? NAMES[type] + "_on"
                : cap && (type == 2 || type == 3) ? NAMES[type] + "_cap" + (active ? "_on" : "")
                : active && (type == 2 || type == 3) ? NAMES[type] + "_on"
                : indicatorLit && module.kind().led() && module.sections() == 1 ? NAMES[type] + "_on" : NAMES[type];
        var model = minecraft.getModelManager().getModel(id(name));
        poses.pushPose();
        poses.translate((x + PcbFootprint.width(definition) / 2f) / 16, 1f / 16,
                (z + PcbFootprint.height(definition) / 2f) / 16);
        poses.mulPose(Axis.YP.rotationDegrees(-90 * PcbWiring.rotation(definition)));
        poses.scale(PcbFootprint.baseWidth(definition), 1, PcbFootprint.baseHeight(definition));
        poses.translate(-0.5, 0, -0.5);
        float tint = burnt ? 0.35f : 1f;
        minecraft.getBlockRenderer().getModelRenderer().renderModel(poses.last(), vertices, null, model,
                tint, tint, tint, indicatorLit && module.sections() == 1 ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light, overlay);
        if (module.sections() > 1) {
            var segment = minecraft.getModelManager().getModel(id("led_strip_segment_on"));
            for (int section = 0; section < module.sections(); section++) {
                if ((sections & (1L << section)) == 0) continue;
                poses.pushPose();
                poses.translate(0, 0, ((section + 0.5f) / module.sections() - 0.5f) * 0.94f / 16);
                minecraft.getBlockRenderer().getModelRenderer().renderModel(poses.last(), vertices, null, segment,
                        1, 1, 1, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay);
                poses.popPose();
            }
        }
        if (module.kind() == PcbCircuitDevice.Kind.RHEOSTAT || module.kind() == PcbCircuitDevice.Kind.POTENTIOMETER) {
            poses.translate(0, 0, (position - 50) / 100f * 0.7f / 16);
            var handle = minecraft.getModelManager().getModel(id("slider_handle"));
            minecraft.getBlockRenderer().getModelRenderer().renderModel(poses.last(), vertices, null, handle,
                    tint, tint, tint, light, overlay);
        }
        if (PcbModules.get(definition).timed()) {
            poses.translate(0.5, 0, 0.5);
            poses.mulPose(Axis.YP.rotationDegrees(-360 * position / 100f));
            poses.translate(-0.5, 0, -0.5);
            var hand = minecraft.getModelManager().getModel(id("timer_hand"));
            minecraft.getBlockRenderer().getModelRenderer().renderModel(poses.last(), vertices, null, hand,
                    active ? 0.4f : tint, tint, active ? 0.4f : tint, light, overlay);
        }
        poses.popPose();
    }
    /** Светодиод: аддитивное свечение щитка EE; крупные элементы: световой слой по Power Grid. */
    static void glow(int definition, int x, int z, long sections, PoseStack poses,
                     net.minecraft.client.renderer.MultiBufferSource buffers,
                     net.minecraft.world.level.block.state.BlockState state, int overlay) {
        if (sections == 0) return;
        var module = PcbModules.get(definition);
        poses.pushPose();
        if (module.kind() == PcbCircuitDevice.Kind.LED) {
            poses.translate((x + 0.5f) / 16, 1.45f / 16, (z + 0.5f) / 16);
            poses.pushPose();
            poses.scale(0.6f, 0.35f, 0.6f);
            poses.translate(-0.25, -0.3125, -0.5);
            net.createmod.catnip.render.CachedBuffers.partial(com.george_vi.electroenergetics.CEEPartialModels.INDICATOR_BULB_GLOW, state)
                    .color(0xFF4828).light(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).disableDiffuse()
                    .renderInto(poses, buffers.getBuffer(com.simibubi.create.foundation.render.RenderTypes.additive()));
            poses.popPose();
            poses.scale(0.38f, 0.24f, 0.38f);
            poses.translate(-0.25, -0.3125, -0.5);
            net.createmod.catnip.render.CachedBuffers.partial(com.george_vi.electroenergetics.CEEPartialModels.INDICATOR_BULB_CUBE, state)
                    .color(0xFFFFFF).light(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).disableDiffuse()
                    .renderInto(poses, buffers.getBuffer(com.simibubi.create.foundation.render.RenderTypes.additive()));
        } else if (module.kind().led() || module.kind() == PcbCircuitDevice.Kind.LAMP) {
            poses.translate((x + PcbFootprint.width(definition) / 2f) / 16, 1f / 16,
                    (z + PcbFootprint.height(definition) / 2f) / 16);
            poses.mulPose(Axis.YP.rotationDegrees(-90 * PcbWiring.rotation(definition)));
            poses.scale(module.width(), 1, module.height());
            poses.translate(-0.5, 0, -0.5);
            var glow = Minecraft.getInstance().getModelManager().getModel(id(module.kind() == PcbCircuitDevice.Kind.LAMP ? "lamp_glow" : module.sections() > 1 ? "light_segment_glow" : "light_glow"));
            var vertices = buffers.getBuffer(com.simibubi.create.foundation.render.RenderTypes.additive());
            for (int section = 0; section < module.sections(); section++) {
                if ((sections & (1L << section)) == 0) continue;
                poses.pushPose();
                if (module.sections() > 1) poses.translate(0, 0, ((section + 0.5f) / module.sections() - 0.5f) * 0.94f / 16);
                Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poses.last(), vertices, null, glow,
                        1, 1, 1, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay);
                poses.popPose();
            }
        }
        poses.popPose();
    }

}
