package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import ee.extras.EeExtras;
import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbItems;
import ee.extras.pcb.PcbTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PcbMountPhotos {
    private static BlockPos support;

    private PcbMountPhotos() {}

    static void tick(Minecraft mc, MinecraftServer server, int tick) {
        if (tick < 100) return;
        int index = (tick - 100) / 60;
        int step = (tick - 100) % 60;
        if (index >= Direction.values().length) return;
        Direction face = Direction.values()[index];
        if (step == 0) {
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var level = player.serverLevel();
                if (support == null) support = player.blockPosition().atY(300);
                BlockPos pedestal = support.east(index * 5);
                var normal = Vec3.atLowerCornerOf(face.getNormal());
                DevTest.place(level, pedestal, Blocks.SMOOTH_STONE.defaultBlockState());
                var topology = PcbTopology.create(1);
                for (int cell = 112; cell < 128; cell++) {
                    int kind = switch (cell) {
                        case 112, 127 -> PcbTopology.TERMINAL;
                        case 119 -> PcbTopology.COMPONENT;
                        default -> PcbTopology.TRACE;
                    };
                    PcbTopology.set(topology, 0, cell, kind);
                }
                int[] parts = new int[PcbTopology.CELLS];
                parts[119] = 1 + 5 * 32; // Резистор 1000 Ом.
                topology.putIntArray("ElectricalParts", parts);
                topology.putBoolean("Assembled", true);
                var stack = PcbItems.L1_BOARD.toStack();
                stack.set(PcbItems.PCB_TOPOLOGY.get(), topology);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                Vec3 target = Vec3.atCenterOf(pedestal).add(normal.scale(0.62));
                player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(target, face, pedestal, false)));
                BlockPos battery = pedestal.south(2).below(2);
                DevTest.place(level, battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
                ClientTest.check("Одножильный провод на грани " + face, DevTest.spoolWire(player,
                        EeExtras.THIN_SINGLE_SPOOL.toStack(), battery, 0, pedestal.relative(face), 0));
                player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                Vec3 eye = target.add(normal.scale(1.8)).add(0.28, face.getAxis().isHorizontal() ? 0.2 : 0, 0.2);
                Vec3 aim = target.subtract(eye);
                float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
                float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
                player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
            });
        }
        if (step == 40) {
            BlockPos mounted = support.east(index * 5).relative(face);
            var state = mc.level.getBlockState(mounted);
            ClientTest.check("Плата на грани " + face, state.is(PcbItems.CIRCUIT_BLOCK.get())
                    && state.getValue(PcbCircuitBlock.FACING) == face);
            ClientTest.check("Попадание по плате на грани " + face, mc.hitResult instanceof BlockHitResult hit
                    && hit.getBlockPos().equals(mounted));
            Screenshot.grab(mc.gameDirectory, "pcb-mount-" + face.getName() + ".png", mc.getMainRenderTarget(),
                    message -> System.out.println(message.getString()));
        }
    }
}
