package ee.extras.wire;

import ee.extras.wire.client.WireClient;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/** События провода: кусачки, клавиша прямой укладки, внеочередная проверка опоры после изменений блоков рядом */
public final class WireEvents {
    /** Кусачки EE и IE (кусачки инженера в этом теге) */
    public static final TagKey<Item> CUTTERS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("electroenergetics", "wire_cutters"));
    private static final Set<UUID> ALTERNATE = new HashSet<>();

    static void register(IEventBus bus) {
        bus.addListener((BlockEvent.BreakEvent e) -> touched(e.getLevel(), e.getPos()));
        bus.addListener((BlockEvent.EntityPlaceEvent e) -> touched(e.getLevel(), e.getPos()));
        bus.addListener((PistonEvent.Post e) -> touched(e.getLevel(), e.getFaceOffsetPos()));
        bus.addListener((ExplosionEvent.Detonate e) -> {
            for (BlockPos p : e.getAffectedBlocks()) touched(e.getLevel(), p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ALTERNATE.remove(e.getEntity().getUUID()));
    }

    static void touched(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof Level l) || l.isClientSide) return;
        for (SurfaceWireEntity w : l.getEntitiesOfClass(SurfaceWireEntity.class, new AABB(pos).inflate(2)))
            w.markSupportDirty();
    }

    static void setAlternate(Player player, boolean on) {
        if (on) ALTERNATE.add(player.getUUID());
        else ALTERNATE.remove(player.getUUID());
    }

    /** Зажата ли клавиша «Прямая укладка» (на клиенте — сама клавиша, на сервере — что прислал клиент) */
    public static boolean alternate(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) return WireClient.alternateDown();
        return ALTERNATE.contains(player.getUUID());
    }

    private WireEvents() {
    }
}
