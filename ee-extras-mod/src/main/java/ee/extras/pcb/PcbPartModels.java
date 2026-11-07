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
        event.register(id("slider_handle"));
        event.register(id("timer_hand"));
        event.register(id("led_on"));
        event.register(id("button_on"));
        event.register(id("switch_on"));
        event.register(id("button_cap"));
        event.register(id("switch_cap"));
        event.register(id("button_cap_on"));
        event.register(id("switch_cap_on"));
    }

    static void render(int definition, int x, int z, boolean burnt, boolean indicatorLit, boolean cap, boolean active, int position, PoseStack poses,
                       VertexConsumer vertices, int light, int overlay) {
        int type = PcbWiring.type(definition);
        if (definition <= 0 || type >= NAMES.length) return;
        var minecraft = Minecraft.getInstance();
        String name = cap && (type == 2 || type == 3) ? NAMES[type] + "_cap" + (active ? "_on" : "")
                : active && (type == 2 || type == 3) ? NAMES[type] + "_on"
                : indicatorLit && NAMES[type].equals("led") ? "led_on" : NAMES[type];
        var model = minecraft.getModelManager().getModel(id(name));
        poses.pushPose();
        poses.translate((x + PcbFootprint.width(definition) / 2f) / 16, 1f / 16,
                (z + PcbFootprint.height(definition) / 2f) / 16);
        poses.mulPose(Axis.YP.rotationDegrees(-90 * PcbWiring.rotation(definition)));
        poses.scale(PcbFootprint.baseWidth(definition), 1, PcbFootprint.baseHeight(definition));
        poses.translate(-0.5, 0, -0.5);
        float tint = burnt ? 0.35f : 1f;
        minecraft.getBlockRenderer().getModelRenderer().renderModel(poses.last(), vertices, null, model,
                tint, tint, tint, indicatorLit ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light, overlay);
        if (PcbModules.get(definition).adjustable()) {
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
}
