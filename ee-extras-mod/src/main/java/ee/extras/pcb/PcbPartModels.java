package ee.extras.pcb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Модели Create: Power Grid, уменьшенные до одной ячейки платы EE. См. NOTICE. */
final class PcbPartModels {
    private static final String[] NAMES = {"resistor", "relay", "button", "switch", "neon_bulb",
            "redstone_relay", "redstone_relay", "capacitor", "diode", "fuse",
            "voltage_gauge", "current_gauge", "bjt_npn", "led"};

    private PcbPartModels() {}

    private static ModelResourceLocation id(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("ee_extras", "block/pcb_part/" + name));
    }

    static void register(ModelEvent.RegisterAdditional event) {
        for (String name : NAMES) event.register(id(name));
        event.register(id("led_on"));
        event.register(id("button_cap"));
        event.register(id("switch_cap"));
    }

    static void render(int definition, int x, int z, boolean burnt, boolean indicatorLit, boolean cap, PoseStack poses,
                       VertexConsumer vertices, int light, int overlay) {
        int type = PcbWiring.type(definition);
        if (definition <= 0 || type >= NAMES.length) return;
        var minecraft = Minecraft.getInstance();
        String name = cap && (type == 2 || type == 3) ? NAMES[type] + "_cap"
                : indicatorLit && NAMES[type].equals("led") ? "led_on" : NAMES[type];
        var model = minecraft.getModelManager().getModel(id(name));
        poses.pushPose();
        poses.translate((x + 0.5f) / 16, 1f / 16, (z + 0.5f) / 16);
        poses.mulPose(Axis.YP.rotationDegrees(-90 * PcbWiring.rotation(definition)));
        poses.translate(-0.5, 0, -0.5);
        float tint = burnt ? 0.35f : 1f;
        minecraft.getBlockRenderer().getModelRenderer().renderModel(poses.last(), vertices, null, model,
                tint, tint, tint, indicatorLit ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light, overlay);
        poses.popPose();
    }
}
