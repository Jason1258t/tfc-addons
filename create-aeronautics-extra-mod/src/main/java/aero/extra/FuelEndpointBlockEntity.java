package aero.extra;

import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FuelEndpointBlockEntity extends BlockEntity {
    private List<BlockPos> hoseEnds = List.of();

    FuelEndpointBlockEntity(BlockPos pos, BlockState state) {
        this(AeroExtra.FUEL_PORT_BE.get(), pos, state);
    }

    FuelEndpointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    List<BlockPos> hoseEnds() {
        return hoseEnds;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (level instanceof ServerLevel server) {
            tag.putLongArray("HoseEnds", FuelHoses.get(server).neighbors(worldPosition).stream()
                    .mapToLong(BlockPos::asLong).toArray());
        }
        return tag;
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        hoseEnds = Arrays.stream(tag.getLongArray("HoseEnds")).mapToObj(BlockPos::of).toList();
    }
}
