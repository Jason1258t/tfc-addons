package ee.extras.devtest;

import ee.extras.EeExtras;
import ee.extras.cable.CableCap;
import ee.extras.cable.CableCapGeometry;
import ee.extras.cable.CableEnd;
import ee.extras.wire.SurfaceWireItem;
import ee.extras.wire.SurfaceWires;
import ee.extras.wire.WireEnd;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/** CAP=1 ./test-client.sh: снимки крышек и получение/снятие крышки на клиенте. */
final class CapClientTest {
    private static final BlockPos MOTOR = CapTest.HEATER.south(2);

    static void tick(Minecraft mc, IntegratedServer server, int tick) {
        if (tick == 40) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            var level = player.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setDayTime(6000);
            player.teleportTo(level, 7.5, -58.5, 147, 0, 30);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        if (tick == 90) server.execute(() -> {
            var level = server.overworld();
            CapTest.build(server.createCommandSourceStack().withLevel(level));
            var motor = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse("electroenergetics:electric_motor"));
            DevTest.place(level, MOTOR, motor.defaultBlockState().rotate(Rotation.CLOCKWISE_90));
            CableCap.put(level, MOTOR, null, new ItemStack(EeExtras.CABLE_CAP.get()));
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            var motorEnd = new WireEnd.Block(MOTOR);
            Vec3 target = motorEnd.position(level).with(net.minecraft.core.Direction.Axis.Y, -59.97);
            var result = SurfaceWireItem.connect(level, new ItemStack(SurfaceWires.DUPLEX_ITEM.get(), 64),
                    player, motorEnd, new WireEnd.Free(target));
            ClientTest.check("провод по поверхности проложен от крышки мотора", result.getResult().consumesAction());
        });
        if (tick == 140) {
            for (BlockPos pos : new BlockPos[]{CapTest.BULB, CapTest.HEATER, CapTest.VM, MOTOR}) {
                var end = CableEnd.of(mc.level, pos, mc.level.getBlockState(pos));
                ClientTest.check("крышка получена: " + pos, end != null && end.contacts() != null);
                if (end != null && end.contacts() != null) {
                    boolean coversEveryState = mc.level.getBlockState(pos).getBlock().getStateDefinition().getPossibleStates()
                            .stream().allMatch(state -> coversBoth(mc.level, pos, state));
                    ClientTest.check("оба контакта внутри крышки во всех состояниях: " + pos, coversEveryState);
                    var cover = CableCapGeometry.of(mc.level, pos, mc.level.getBlockState(pos), end.contacts());
                    ClientTest.check("декоративный провод подходит к вводу: " + pos, cover != null
                            && new WireEnd.Block(pos).position(mc.level).equals(Vec3.atLowerCornerOf(pos).add(cover.inlet())));
                }
            }
            WireClientTest.look(mc, Vec3.atCenterOf(CapTest.HEATER));
        }
        if (tick == 150) {
            Screenshot.grab(mc.gameDirectory, "cable_caps.png", mc.getMainRenderTarget(), message -> {});
            frame(mc, server, CapTest.BULB);
        }
        if (tick == 175) {
            WireClientTest.look(mc, Vec3.atCenterOf(CapTest.BULB));
        }
        if (tick == 180) {
            Screenshot.grab(mc.gameDirectory, "cable_cap_bulb.png", mc.getMainRenderTarget(), message -> {});
            frame(mc, server, MOTOR);
        }
        if (tick == 190) com.george_vi.electroenergetics.config.CEEConfigs.client().disableFlywheelWireRendering.set(true);
        if (tick == 205) WireClientTest.look(mc, Vec3.atCenterOf(MOTOR));
        if (tick == 210) {
            Screenshot.grab(mc.gameDirectory, "cable_cap_motor.png", mc.getMainRenderTarget(), message -> {});
            server.execute(() -> CapTest.take(server.createCommandSourceStack()));
        }
        if (tick == 230) {
            ClientTest.check("снятая крышка исчезла на клиенте",
                    CableEnd.of(mc.level, CapTest.HEATER, mc.level.getBlockState(CapTest.HEATER)) == null);
            mc.stop();
        }
    }

    private static boolean coversBoth(net.minecraft.world.level.Level level, BlockPos pos,
                                      net.minecraft.world.level.block.state.BlockState state) {
        int[] contacts = CableCap.contacts(level, pos, state);
        if (contacts == null || contacts.length != 2) return false;
        var cover = CableCapGeometry.of(level, pos, state, contacts);
        if (cover == null) return false;
        var device = (com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock<?>) state.getBlock();
        for (int contact : contacts) {
            Vec3 node = device.getNodePosition(level, pos, state, contact);
            Vec3 delta = node.subtract(cover.front());
            if (delta.dot(cover.outward()) >= 0 || node.subtract(cover.back()).dot(cover.outward()) <= 0
                    || Math.abs(delta.dot(cover.length().normalize())) >= cover.length().length()
                    || Math.abs(delta.dot(cover.width().normalize())) >= cover.width().length()) return false;
        }
        return true;
    }

    private static void frame(Minecraft mc, IntegratedServer server, BlockPos pos) {
        var state = mc.level.getBlockState(pos);
        var geometry = CableCapGeometry.of(mc.level, pos, state, CableCap.contacts(mc.level, pos, state));
        Vec3 camera = Vec3.atLowerCornerOf(pos).add(geometry.front()).add(geometry.outward().scale(2.5));
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(player.serverLevel(), camera.x, Math.max(-59.9, camera.y - player.getEyeHeight()),
                    camera.z + 0.5, 0, 0);
        });
    }

    private CapClientTest() {
    }
}
