package ee.extras.wire;

import ee.extras.EeExtras;
import ee.extras.wire.client.WireClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Пакеты провода: данные сущности сервер → клиент; с клиента — ответвление/продолжение по клику на провод, вырезка
 * участка кусачками, клавиша прямой укладки.
 *
 * Перенесено из Create: Power Grid 0.6.2 (EntityDataS2CPacket, BlockWireAttachC2SPacket, BlockWireCutC2SPacket,
 * AlternatePlacementStatusC2SPacket; Apache-2.0, см. NOTICE). Изменения: пакеты NeoForge, проверка расстояния до
 * провода, отрезок — в шагах от начала провода.
 */
public final class WirePackets {
    public record Sync(int entity, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(EeExtras.rl("surface_wire_sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Sync::entity,
                ByteBufCodecs.COMPOUND_TAG, Sync::data, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    public record Attach(int entity, int index, int point) implements CustomPacketPayload {
        public static final Type<Attach> TYPE = new Type<>(EeExtras.rl("surface_wire_attach"));
        public static final StreamCodec<ByteBuf, Attach> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Attach::entity,
                ByteBufCodecs.VAR_INT, Attach::index, ByteBufCodecs.VAR_INT, Attach::point, Attach::new);

        @Override
        public Type<Attach> type() {
            return TYPE;
        }
    }

    /** Вырезать участок между шагами from и to от начала провода */
    public record Cut(int entity, int from, int to) implements CustomPacketPayload {
        public static final Type<Cut> TYPE = new Type<>(EeExtras.rl("surface_wire_cut"));
        public static final StreamCodec<ByteBuf, Cut> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Cut::entity,
                ByteBufCodecs.VAR_INT, Cut::from, ByteBufCodecs.VAR_INT, Cut::to, Cut::new);

        @Override
        public Type<Cut> type() {
            return TYPE;
        }
    }

    public record Alternate(boolean on) implements CustomPacketPayload {
        public static final Type<Alternate> TYPE = new Type<>(EeExtras.rl("surface_wire_alternate"));
        public static final StreamCodec<ByteBuf, Alternate> CODEC = ByteBufCodecs.BOOL.map(Alternate::new, Alternate::on);

        @Override
        public Type<Alternate> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Sync.TYPE, Sync.CODEC, (p, ctx) -> WireClient.sync(p));
        r.playToServer(Attach.TYPE, Attach.CODEC, WirePackets::attach);
        r.playToServer(Cut.TYPE, Cut.CODEC, WirePackets::cut);
        r.playToServer(Alternate.TYPE, Alternate.CODEC, (p, ctx) -> WireEvents.setAlternate(ctx.player(), p.on()));
    }

    static SurfaceWireEntity wire(ServerPlayer player, int id) {
        if (!(player.serverLevel().getEntity(id) instanceof SurfaceWireEntity w)) return null;
        return player.canInteractWithEntity(w.getBoundingBox(), 4.0) ? w : null;
    }

    static void attach(Attach p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        SurfaceWireEntity wire = wire(player, p.entity());
        if (wire != null) attachAt(player, wire, p.index(), p.point());
    }

    /** Клик проводом по проводу: край — продолжение или сращивание, середина — будущий узел ответвления */
    public static void attachAt(ServerPlayer player, SurfaceWireEntity wire, int index, int point) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof SurfaceWireItem) || index < 0 || index >= wire.segments.size()) return;
        Point seg = wire.segments.get(index);
        if (point < 0 || point > seg.gridLength) return;
        WireEnd end;
        if (point <= 1 && index == 0) {
            if (wire.end1() == null) {
                wire.flip();
                end = new WireEnd.WireTip(wire, true);
            } else end = wire.end1();
        } else if (point < seg.gridLength - 1 || index != wire.segments.size() - 1) {
            end = new WireEnd.Deferred(wire.getUUID(), wire.blockPosition(), index, point);
        } else {
            end = wire.end2() == null ? new WireEnd.WireTip(wire, true) : wire.end2();
        }
        WireEnd existing = SurfaceWireItem.selection(stack);
        if (existing == null) {
            SurfaceWireItem.select(stack, end);
            SurfaceWireItem.message(player, "ee_extras.surface_wire.next", ChatFormatting.GRAY);
            return;
        }
        InteractionResultHolder<SurfaceWireEntity> r = SurfaceWireItem.connect(player.level(), stack, player, existing, end);
        if (r.getResult().consumesAction()) SurfaceWireItem.select(stack, null);
    }

    static void cut(Cut p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        SurfaceWireEntity wire = wire(player, p.entity());
        if (wire == null || !player.getMainHandItem().is(WireEvents.CUTTERS)) return;
        wire.cutRange(Math.min(p.from(), p.to()), Math.max(p.from(), p.to()), player);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    private WirePackets() {
    }
}
