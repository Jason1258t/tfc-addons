package ee.extras.motor;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class BeltMotorItem extends Item {
    public BeltMotorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var side = context.getClickedFace();
        var state = level.getBlockState(pos);
        var player = context.getPlayer();
        if (player == null || !player.mayBuild() || !BeltMotorDevice.supports(state)
                || !(state.getBlock() instanceof BeltBlock belt) || side.getAxis() != belt.getRotationAxis(state)
                || !(level.getBlockEntity(pos) instanceof BeltBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        var devices = DevicesSavedData.load(server);
        if (devices.getDevice(pos) != null) return InteractionResult.FAIL;
        CompoundTag data = new CompoundTag();
        data.putString(BeltMotorDevice.MOUNT_SIDE, side.getSerializedName());
        devices.addDevice(BeltMotorDevice.TYPE, pos, data);
        InfrastructureSavedData.load(server).registerOrUpdateNodes(pos, List.of(0, 1));
        if (!player.isCreative()) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
