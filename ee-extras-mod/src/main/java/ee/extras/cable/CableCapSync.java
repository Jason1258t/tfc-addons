package ee.extras.cable;

import ee.extras.EeExtras;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record CableCapSync(CompoundTag data) implements CustomPacketPayload {
    private static final Type<CableCapSync> TYPE = new Type<>(EeExtras.rl("cable_caps"));
    private static final StreamCodec<ByteBuf, CableCapSync> CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(CableCapSync::new, CableCapSync::data);

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(CableCapSync::registerPayload);
        gameBus.addListener(CableCapSync::chunkSent);
    }

    private static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (packet, context) -> CableCapRender.sync(context.player().level(), packet));
    }

    private static void chunkSent(ChunkWatchEvent.Sent event) {
        CableCapSync packet = snapshot(event.getLevel(), event.getPos());
        if (packet.data().getLongArray("Caps").length != 0) PacketDistributor.sendToPlayer(event.getPlayer(), packet);
    }

    static void changed(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, snapshot(level, chunk));
    }

    private static CableCapSync snapshot(ServerLevel level, ChunkPos chunk) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Chunk", chunk.toLong());
        var caps = CableCap.get(level).caps.stream().filter(c -> new ChunkPos(c.pos()).equals(chunk)).toList();
        tag.putLongArray("Caps", caps.stream().map(CableCap.Cap::pos).distinct().mapToLong(BlockPos::asLong).toArray());
        tag.put("Pairs", CableCap.encode(caps));
        return new CableCapSync(tag);
    }

    @Override
    public Type<CableCapSync> type() {
        return TYPE;
    }
}
