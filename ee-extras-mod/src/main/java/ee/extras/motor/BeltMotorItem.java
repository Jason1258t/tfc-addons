package ee.extras.motor;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllItems;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class BeltMotorItem extends Item {
    public BeltMotorItem(Properties properties) {
        super(properties);
    }

    public static void useWrench(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        var player = event.getEntity();
        if (event.isCanceled() || event.getFace() == null || event.getHand() != InteractionHand.MAIN_HAND || !player.mayBuild()
                || !AllItems.WRENCH.isIn(event.getItemStack())
                || !(level.getBlockEntity(pos) instanceof BeltMotorHost host)
                || host.eeExtras$mountSide() != event.getFace()) return;

        if (level instanceof ServerLevel server) {
            var devices = DevicesSavedData.load(server);
            if (devices.getDevice(pos) instanceof BeltMotorDevice drive) {
                if (player.isShiftKeyDown()) {
                    devices.removeDevice(pos);
                } else {
                    drive.reverse();
                    player.displayClientMessage(Component.translatable(
                            drive.rpm() < 0 ? "item.ee_extras.belt_motor.reverse" : "item.ee_extras.belt_motor.forward"), true);
                }
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        event.setCanceled(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ee_extras.belt_motor.mount"));
        tooltip.add(Component.translatable("item.ee_extras.belt_motor.wrench"));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var side = context.getClickedFace();
        var state = level.getBlockState(pos);
        var player = context.getPlayer();
        if (player == null || !player.mayBuild() || !BeltMotorDevice.supportsSide(state, side)
                || !(level.getBlockEntity(pos) instanceof BeltMotorHost host)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        var devices = DevicesSavedData.load(server);
        if (devices.getDevice(pos) != null) return InteractionResult.FAIL;
        CompoundTag data = new CompoundTag();
        data.putString(BeltMotorDevice.MOUNT_SIDE, side.getSerializedName());
        devices.addDevice(BeltMotorDevice.TYPE, pos, data);
        host.eeExtras$mountSide(side);
        InfrastructureSavedData.load(server).registerOrUpdateNodes(pos, List.of(0, 1));
        if (!player.isCreative()) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
