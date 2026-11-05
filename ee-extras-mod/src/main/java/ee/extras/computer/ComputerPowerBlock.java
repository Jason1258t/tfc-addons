package ee.extras.computer;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import ee.extras.EeExtras;
import ee.extras.cable.DuplexInputBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class ComputerPowerBlock extends DuplexInputBlock {
    private final boolean charger;

    public ComputerPowerBlock(Properties properties, boolean charger) {
        super(properties);
        this.charger = charger;
    }

    public boolean isCharger() {
        return charger;
    }

    @Override
    public SimulatedDeviceType<BundledWireTerminationDevice> getDevice() {
        return EeExtras.COMPUTER_POWER_DEVICE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && (!charger || !PocketCharge.isPocket(stack))) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level instanceof ServerLevel server) {
            ComputerPowerDevice device = DevicesSavedData.load(server).getDevice(pos, ComputerPowerDevice.class);
            if (device == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            Component status = device.status();
            if (charger) {
                if (stack.isEmpty() && !player.isShiftKeyDown()) {
                    ItemStack pocket = device.takePocket();
                    if (!pocket.isEmpty()) player.setItemInHand(hand, pocket);
                } else if (!stack.isEmpty() && device.insertPocket(stack)) {
                    stack.shrink(1);
                    status = device.status();
                }
            }
            player.displayClientMessage(status, true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (charger && !state.is(replacement.getBlock()) && level instanceof ServerLevel server) {
            ComputerPowerDevice device = DevicesSavedData.load(server).getDevice(pos, ComputerPowerDevice.class);
            if (device != null) {
                ItemStack pocket = device.takePocket();
                if (!pocket.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pocket);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
