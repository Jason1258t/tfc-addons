package ee.extras.pcb;

import ee.extras.EeExtras;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Параметры принимает только открытое меню с тем же выбранным экземпляром. */
public final class PcbEditorPackets {
    private PcbEditorPackets() {}
    public record Parameters(int container, int anchor, int definition, double nominal, double threshold) implements CustomPacketPayload {
        private static final Type<Parameters> TYPE = new Type<>(EeExtras.rl("pcb_parameters"));
        private static final StreamCodec<ByteBuf, Parameters> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Parameters::container, ByteBufCodecs.INT, Parameters::anchor,
                ByteBufCodecs.VAR_INT, Parameters::definition, ByteBufCodecs.DOUBLE, Parameters::nominal,
                ByteBufCodecs.DOUBLE, Parameters::threshold, Parameters::new);
        @Override public Type<Parameters> type() { return TYPE; }
    }

    public static void register(IEventBus bus) { bus.addListener(PcbEditorPackets::registerPayload); }

    private static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Parameters.TYPE, Parameters.CODEC, (packet, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof PcbEditorMenu menu && menu.containerId == packet.container() && menu.stillValid(player))
                menu.applyParameters(packet);
        });
    }
}
