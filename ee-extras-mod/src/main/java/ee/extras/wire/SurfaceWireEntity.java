package ee.extras.wire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import ee.extras.wire.client.WireClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Декоративный провод: одна сущность на кусок между двумя концами. Позиция — начало, дальше отрезки вдоль осей
 * (Point, шаг 1/16 блока). Кусок с двумя подключёнными концами (прибор или узел ответвления) — по жиле EE на жилу
 * провода, длина жилы — длина маршрута; свободный край жил не имеет.
 *
 * Перенесено из Create: Power Grid 0.6.2 (BlockWireEntity, WireEntity, BaseWireEntity; Apache-2.0, авторы patryk3211,
 * Da-Negy, rvndm, casvara; см. NOTICE). Изменения: вместо расчёта сети Power Grid — жилы EE (как у двухжильного кабеля,
 * ee.extras.cable), перегрев — от температуры жилы EE; переворот на месте (UUID не меняется); при делении предметы
 * не удваиваются; своя проверка опоры (в Power Grid её нет); без Sable, ток от касания не бьёт.
 */
public class SurfaceWireEntity extends Entity implements IEntityWithComplexSpawn {
    static final EntityDataAccessor<Boolean> HOT = SynchedEntityData.defineId(SurfaceWireEntity.class, EntityDataSerializers.BOOLEAN);
    static final int CHECK_TICKS = 10;
    static final int SUPPORT_TICKS = 100;
    /** Сколько шагов сетки подряд провод может идти без опоры — как у поиска пути (BlockTrace) */
    static final int MAX_UNSUPPORTED = 16;

    public final List<Point> segments = new ArrayList<>();
    /** Рамки отрезков относительно позиции — для наведения */
    public final List<AABB> boxes = new ArrayList<>();
    private AABB mainBox;
    private float totalLength;
    private WireEnd end1;
    private WireEnd end2;
    private Item item = Items.AIR;
    private int itemCount;
    private int color = -1;
    /** Жилы EE: {узел на конце 1, узел на конце 2} */
    final List<InWorldNode[]> cores = new ArrayList<>();
    private boolean supportDirty;

    public SurfaceWireEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SurfaceWireEntity create(Level level, Vec3 start, Item item, int count, List<Point> segments) {
        SurfaceWireEntity e = new SurfaceWireEntity(SurfaceWires.WIRE_ENTITY.get(), level);
        e.setItem(item, count);
        e.setPosRaw(start.x, start.y, start.z);
        e.segments.addAll(segments);
        e.bake();
        if (e.spec().colorable()) e.color = e.spec().defaultColor();
        e.setOldPosAndRot();
        return e;
    }

    // ---------------------------------------------------------------- геометрия

    public SurfaceWires.Spec spec() {
        SurfaceWires.Spec s = SurfaceWires.spec(item);
        return s != null ? s : SurfaceWires.DUPLEX;
    }

    private void optimize() {
        segments.removeIf(s -> s.gridLength <= 0);
        for (int i = 1; i < segments.size(); ) {
            Point prev = segments.get(i - 1), cur = segments.get(i);
            if (prev.direction == cur.direction) {
                segments.set(i - 1, new Point(prev.direction, prev.gridLength + cur.gridLength));
                segments.remove(i);
            } else i++;
        }
    }

    public void bake() {
        optimize();
        boxes.clear();
        totalLength = 0;
        Vec3 cur = Vec3.ZERO;
        double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (Point s : segments) {
            s.start = cur.add(position());
            Vec3 next = cur.add(s.vector());
            minX = Math.min(minX, next.x);
            minY = Math.min(minY, next.y);
            minZ = Math.min(minZ, next.z);
            maxX = Math.max(maxX, next.x);
            maxY = Math.max(maxY, next.y);
            maxZ = Math.max(maxZ, next.z);
            boxes.add(new AABB(cur, next).inflate(0.0625));
            cur = next;
            totalLength += s.length();
        }
        mainBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.0625);
        setBoundingBox(makeBoundingBox());
    }

    @Override
    protected AABB makeBoundingBox() {
        return mainBox != null ? mainBox.move(position()) : super.makeBoundingBox();
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (mainBox != null) bake();
    }

    public float totalLength() {
        return totalLength;
    }

    public int gridLength() {
        int n = 0;
        for (Point s : segments) n += s.gridLength;
        return n;
    }

    public Vec3 endPosition() {
        Vec3 p = position();
        for (Point s : segments) p = p.add(s.vector());
        return p;
    }

    /** Точка маршрута через v шагов сетки от начала */
    public Vec3 at(int v) {
        Vec3 p = position();
        for (Point s : segments) {
            if (v <= s.gridLength) return p.relative(s.direction, (double) v / Point.GRID);
            v -= s.gridLength;
            p = p.add(s.vector());
        }
        return p;
    }

    /** Отрезки маршрута между шагами from и to от начала */
    List<Point> slice(int from, int to) {
        List<Point> out = new ArrayList<>();
        int pos = 0;
        for (Point s : segments) {
            int a = Math.max(from, pos), b = Math.min(to, pos + s.gridLength);
            if (b > a) out.add(new Point(s.direction, b - a));
            pos += s.gridLength;
        }
        return out;
    }

    // ---------------------------------------------------------------- предметы, цвет

    public void setItem(Item item, int count) {
        this.item = item;
        this.itemCount = Math.max(count, 0);
    }

    public Item item() {
        return item;
    }

    public int itemCount() {
        return itemCount;
    }

    public void addItems(int n) {
        itemCount = Math.max(0, itemCount + n);
    }

    public int color() {
        return spec().colorable() ? color : -1;
    }

    public boolean hot() {
        return entityData.get(HOT);
    }

    // ---------------------------------------------------------------- концы и жилы

    public WireEnd end1() {
        return end1;
    }

    public WireEnd end2() {
        return end2;
    }

    public void setEnd1(WireEnd e) {
        WireEnd old = end1;
        end1 = resolve(e);
        endChanged(old, end1);
    }

    public void setEnd2(WireEnd e) {
        WireEnd old = end2;
        end2 = resolve(e);
        endChanged(old, end2);
    }

    /** Для сращивания: концы переходят к другому куску без отсоединения от узлов */
    void forgetEnds() {
        end1 = null;
        end2 = null;
    }

    void setEnd2Raw(WireEnd e) {
        end2 = e;
    }

    private WireEnd resolve(WireEnd e) {
        if (e instanceof WireEnd.Deferred d && level() instanceof ServerLevel sl) return d.resolve(sl);
        return e;
    }

    private void endChanged(WireEnd old, WireEnd now) {
        if (WireEnd.same(old, now)) return;
        if (!(level() instanceof ServerLevel sl)) return;
        if (now instanceof WireEnd.Junction j) JunctionData.get(sl).attach(j.id(), getUUID());
        rewire();
        if (old instanceof WireEnd.Junction j) JunctionData.get(sl).detach(sl, j.id(), getUUID());
    }

    /** Жилы заново: старые убрать, при двух подключённых концах на месте — проложить по длине маршрута */
    public void rewire() {
        if (!(level() instanceof ServerLevel sl)) return;
        dropCores();
        if (end1 == null || end2 == null || !end1.type().connectable || !end2.type().connectable) return;
        int n = spec().conductors();
        List<InWorldNode> a = end1.nodes(sl, n), b = end2.nodes(sl, n);
        if (a == null || b == null) return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        for (int k = 0; k < n; k++) if (!sd.hasNode(a.get(k)) || !sd.hasNode(b.get(k)) || a.get(k).equals(b.get(k))) return;
        for (int k = 0; k < n; k++) {
            sd.connect(a.get(k), b.get(k), WireData.ofLength(spec().core().get(), Math.max(totalLength, 0.0625)));
            cores.add(new InWorldNode[] {a.get(k), b.get(k)});
        }
    }

    void dropCores() {
        if (!(level() instanceof ServerLevel sl) || cores.isEmpty()) return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        for (InWorldNode[] c : cores)
            if (sd.hasNode(c[0]) && sd.hasNode(c[1]) && sd.isConnected(c[0], c[1]))
                sd.removeConnectionNoDrops(new InWorldNodeConnection(c[0], c[1]));
        cores.clear();
    }

    /** Сверка с миром раз в CHECK_TICKS: прибор на конце сломан, узел пропал, жила сгорела, жилы не проложены */
    void check(ServerLevel sl) {
        for (int i = 1; i <= 2; i++) {
            WireEnd e = i == 1 ? end1 : end2;
            if (e instanceof WireEnd.Block b && b.loaded(sl) && !b.valid(sl)) {
                endpointRemoved(e);
                return;
            }
            if (e instanceof WireEnd.Junction j && !j.valid(sl)) {
                if (i == 1) end1 = null;
                else end2 = null;
                dropCores();
                sync();
            }
        }
        if (isRemoved()) return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        if (!cores.isEmpty()) {
            float temp = 0;
            for (InWorldNode[] c : cores) {
                if (!sd.hasNode(c[0]) || !sd.hasNode(c[1]) || !sd.isConnected(c[0], c[1])) {
                    burn(sl);
                    return;
                }
                WireData d = sd.getConnectionData(new InWorldNodeConnection(c[0], c[1]));
                if (d != null) temp = Math.max(temp, d.temperature());
            }
            entityData.set(HOT, temp > spec().core().get().getMaxTemperature() * 0.85);
        } else {
            entityData.set(HOT, false);
            if (end1 != null && end2 != null && end1.type().connectable && end2.type().connectable && end1.valid(sl) && end2.valid(sl))
                rewire();
        }
    }

    /** Жила сгорела: кусок исчезает без выпадения, с пламенем по маршруту */
    void burn(ServerLevel sl) {
        for (Point s : segments) {
            int n = Math.max(1, Math.round(s.length() / 0.25f));
            for (int i = 0; i < n; i++) {
                Vec3 p = s.start.add(s.vector().scale((double) i / n));
                sl.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.2f);
        itemCount = 0;
        discard();
    }

    /**
     * Прибор на конце пропал: последний отрезок у этого конца выпадает предметами, конец свободен (как
     * BlockWireEntity.endpointRemoved в Power Grid)
     */
    public void endpointRemoved(WireEnd e) {
        Point removed;
        boolean atEnd = WireEnd.same(e, end2);
        if (!atEnd && !WireEnd.same(e, end1)) return;
        if (segments.isEmpty()) {
            kill();
            return;
        }
        if (atEnd) {
            removed = segments.remove(segments.size() - 1);
        } else {
            removed = segments.remove(0);
            Vec3 p = position().add(removed.vector());
            setPosRaw(p.x, p.y, p.z);
        }
        bake();
        if (segments.isEmpty()) {
            kill();
            return;
        }
        int keep = Math.min(itemCount, Math.max(1, WireConfig.items(totalLength)));
        int drop = itemCount - keep;
        if (drop > 0) {
            Vec3 p = atEnd ? endPosition() : position();
            spawn(new ItemStack(item, drop), p);
            itemCount = keep;
        }
        if (atEnd) setEnd2(null);
        else setEnd1(null);
        sync();
    }

    /** Перевернуть на месте: начало ↔ конец (Power Grid создавал новую сущность) */
    public void flip() {
        Vec3 p = endPosition();
        List<Point> rev = new ArrayList<>();
        for (Point s : segments) rev.add(0, s.reversed());
        segments.clear();
        segments.addAll(rev);
        setPosRaw(p.x, p.y, p.z);
        WireEnd e = end1;
        end1 = end2;
        end2 = e;
        for (InWorldNode[] c : cores) {
            InWorldNode t = c[0];
            c[0] = c[1];
            c[1] = t;
        }
        bake();
        sync();
    }

    public void extend(List<Point> points, int newItems) {
        addItems(newItems);
        segments.addAll(points);
        bake();
    }

    /**
     * Разделить провод в точке point отрезка index: там встаёт узел ответвления, хвост — новый кусок. Геометрия — как
     * у Power Grid: этот кусок кончается на шаг раньше узла, новый начинается в узле (отрисовка продлевает первый отрезок
     * на шаг назад).
     */
    public WireEnd.Junction split(int index, int point) {
        if (!(level() instanceof ServerLevel sl) || segments.isEmpty()) return null;
        index = Math.clamp(index, 0, segments.size() - 1);
        if (point <= 0 && index > 0) {
            index--;
            point = segments.get(index).gridLength;
        }
        Point seg = segments.get(index);
        point = Math.clamp(point, 1, seg.gridLength);
        Vec3 at = seg.start.relative(seg.direction, (double) point / Point.GRID);

        List<Point> tail = new ArrayList<>();
        tail.add(new Point(seg.direction, seg.gridLength - point));
        while (segments.size() > index + 1) tail.add(segments.remove(index + 1));
        segments.set(index, new Point(seg.direction, point - 1));

        SurfaceWireEntity w2 = new SurfaceWireEntity(SurfaceWires.WIRE_ENTITY.get(), sl);
        w2.setItem(item, 0);
        w2.color = color;
        w2.setPosRaw(at.x, at.y, at.z);
        w2.segments.addAll(tail);
        w2.bake();
        bake();
        int moved = Math.min(itemCount, WireConfig.items(w2.totalLength));
        w2.itemCount = moved;
        itemCount -= moved;

        dropCores();
        JunctionData data = JunctionData.get(sl);
        WireEnd.Junction j = data.create(sl, at, spec().conductors());
        WireEnd oldEnd2 = end2;
        end2 = j;
        data.attach(j.id(), getUUID());
        w2.end1 = j;
        data.attach(j.id(), w2.getUUID());
        w2.end2 = oldEnd2;
        if (oldEnd2 instanceof WireEnd.Junction o) data.replace(o.id(), getUUID(), w2.getUUID());
        sl.addFreshEntity(w2);
        rewire();
        w2.rewire();
        w2.sync();
        sync();
        return j;
    }

    /**
     * В узле осталось два куска — срастить в один (JunctionData.detach). false — один из них не загружен, узел пока
     * остаётся. Как JunctionWireEndpoint.removeWireEntity в Power Grid.
     */
    static boolean mergeAt(ServerLevel sl, WireEnd.Junction j, List<UUID> wires) {
        if (!(sl.getEntity(wires.get(0)) instanceof SurfaceWireEntity a) || !(sl.getEntity(wires.get(1)) instanceof SurfaceWireEntity b))
            return false;
        if (a == b) return false;
        a.dropCores();
        b.dropCores();
        // target кончается в узле (конец 2), source начинается в нём (конец 1)
        if (!WireEnd.same(a.end2, j)) a.flip();
        if (!WireEnd.same(b.end1, j)) b.flip();
        if (!a.segments.isEmpty()) {
            int last = a.segments.size() - 1;
            Point p = a.segments.get(last);
            a.segments.set(last, new Point(p.direction, p.gridLength + 1));
        }
        a.extend(new ArrayList<>(b.segments), b.itemCount);
        WireEnd far = b.end2;
        b.end1 = null;
        b.end2 = null;
        b.itemCount = 0;
        a.end2 = far;
        if (far instanceof WireEnd.Junction o) JunctionData.get(sl).replace(o.id(), b.getUUID(), a.getUUID());
        b.discard();
        if (a.segments.isEmpty()) a.kill();
        else {
            a.rewire();
            a.sync();
        }
        return true;
    }

    /** Узел убран, этот кусок в нём был последним — край свободен (жилы к узлу уже сняты вместе с его узлами EE) */
    static void junctionLost(ServerLevel sl, UUID wire, WireEnd.Junction j) {
        if (!(sl.getEntity(wire) instanceof SurfaceWireEntity w)) return;
        w.dropCores();
        if (WireEnd.same(w.end1, j)) w.end1 = null;
        if (WireEnd.same(w.end2, j)) w.end2 = null;
        w.sync();
    }

    /**
     * Вырезать участок между шагами v1 и v2 от начала: остаётся [0, v1] и [v2, конец] (второе — новым куском).
     * Куски короче 3 шагов убираются. Лишние предметы — игроку или на землю. Как BlockWireCutC2SPacket Power Grid.
     */
    public void cutRange(int v1, int v2, Player player) {
        if (!(level() instanceof ServerLevel sl)) return;
        int total = gridLength();
        v1 = Math.clamp(v1, 0, total);
        v2 = Math.clamp(v2, v1, total);
        Vec3 secondStart = at(v2);
        List<Point> first = slice(0, v1), second = slice(v2, total);
        int len1 = v1, len2 = total - v2;
        int items = itemCount;
        WireEnd oldEnd2 = end2;
        Vec3 dropAt = at((v1 + v2) / 2);

        SurfaceWireEntity w2 = null;
        if (len2 >= 3) {
            w2 = new SurfaceWireEntity(SurfaceWires.WIRE_ENTITY.get(), sl);
            w2.setItem(item, 0);
            w2.color = color;
            w2.setPosRaw(secondStart.x, secondStart.y, secondStart.z);
            w2.segments.addAll(second);
            w2.bake();
            w2.itemCount = Math.min(items, WireConfig.items((double) len2 / Point.GRID));
            items -= w2.itemCount;
        }
        dropCores();
        // Конец 2 переходит ко второму куску или (его нет) освобождается
        end2 = null;
        if (w2 != null) {
            w2.end2 = oldEnd2;
            if (oldEnd2 instanceof WireEnd.Junction o) JunctionData.get(sl).replace(o.id(), getUUID(), w2.getUUID());
        } else if (oldEnd2 instanceof WireEnd.Junction o) JunctionData.get(sl).detach(sl, o.id(), getUUID());

        if (len1 >= 3) {
            segments.clear();
            segments.addAll(first);
            bake();
            int keep = Math.min(items, WireConfig.items((double) len1 / Point.GRID));
            itemCount = keep;
            items -= keep;
        } else {
            itemCount = 0;
        }
        if (items > 0) give(player, new ItemStack(item, items), dropAt);
        if (w2 != null) {
            sl.addFreshEntity(w2);
            w2.rewire();
            w2.sync();
        }
        if (len1 < 3) discard();
        else {
            rewire();
            sync();
        }
    }

    /** Снять целиком: предметы игроку (кусачками) или на землю */
    public void removeWhole(Player player) {
        int n = itemCount;
        itemCount = 0;
        if (n > 0) give(player, new ItemStack(item, n), mainBox != null ? mainBox.getCenter().add(position()) : position());
        discard();
    }

    void give(Player player, ItemStack stack, Vec3 at) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(stack.getMaxStackSize());
            if (player != null) player.getInventory().placeItemBackInInventory(part);
            else spawn(part, at);
        }
    }

    void spawn(ItemStack stack, Vec3 at) {
        while (!stack.isEmpty()) {
            ItemEntity e = new ItemEntity(level(), at.x, at.y, at.z, stack.split(stack.getMaxStackSize()));
            e.setDefaultPickUpDelay();
            level().addFreshEntity(e);
        }
    }

    // ---------------------------------------------------------------- опора

    public void markSupportDirty() {
        supportDirty = true;
    }

    /**
     * Своё, в Power Grid этого нет: шаг маршрута внутри формы блока или больше MAX_UNSUPPORTED шагов подряд без
     * опоры (соседний блок-проводник, как у BlockTrace) — участок вырезается и выпадает. Шаги в блоках приборов на
     * концах не проверяются (провод заходит в их форму).
     */
    void supportCheck(ServerLevel sl) {
        supportDirty = false;
        int total = gridLength();
        if (total == 0) return;
        BlockPos skip1 = end1 instanceof WireEnd.Block b ? b.pos() : null;
        BlockPos skip2 = end2 instanceof WireEnd.Block b ? b.pos() : null;
        int run = 0, runStart = 0;
        for (int v = 0; v <= total; v++) {
            Vec3 p = at(v);
            BlockPos bp = BlockPos.containing(p);
            if (!sl.isLoaded(bp)) return;
            boolean device = bp.equals(skip1) || bp.equals(skip2);
            if (!device && inside(sl, bp, p)) {
                int a = Math.max(0, v - 1), b = v + 1;
                while (b <= total && inside(sl, BlockPos.containing(at(b)), at(b))) b++;
                cutRange(a, Math.min(total, b), null);
                return;
            }
            if (device || supported(sl, p)) {
                run = 0;
                continue;
            }
            if (run++ == 0) runStart = v;
            if (run > MAX_UNSUPPORTED) {
                int b = v;
                while (b < total && !supported(sl, at(b + 1))) b++;
                cutRange(Math.max(0, runStart - 1), Math.min(total, b + 1), null);
                return;
            }
        }
    }

    static boolean inside(Level level, BlockPos pos, Vec3 p) {
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return false;
        Vec3 local = p.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB box : st.getCollisionShape(level, pos).toAabbs()) if (box.contains(local)) return true;
        return false;
    }

    static boolean supported(Level level, Vec3 p) {
        for (Direction d : Direction.values()) {
            BlockPos bp = BlockPos.containing(p.relative(d, 1.0 / Point.GRID));
            if (level.getBlockState(bp).isRedstoneConductor(level, bp)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- тик

    @Override
    public void baseTick() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            int phase = Math.floorMod(getId(), SUPPORT_TICKS);
            if (tickCount % CHECK_TICKS == phase % CHECK_TICKS) check(sl);
            if (!isRemoved() && (supportDirty || tickCount % SUPPORT_TICKS == phase)) supportCheck(sl);
        } else if (hot() && !segments.isEmpty() && random.nextInt(4) == 0) {
            Point s = segments.get(random.nextInt(segments.size()));
            Vec3 p = s.start.add(s.vector().scale(random.nextFloat()));
            level().addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.05, 0);
        }
    }

    // ---------------------------------------------------------------- взаимодействие

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        Level level = player.level();
        if (stack.getItem() instanceof SurfaceWireItem) {
            return level.isClientSide ? WireClient.attach(this) : InteractionResult.CONSUME;
        }
        if (stack.is(WireEvents.CUTTERS)) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8f, 1.2f);
                    removeWhole(player);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return level.isClientSide ? WireClient.cut(this) : InteractionResult.CONSUME;
        }
        if (stack.getItem() instanceof DyeItem dye && spec().colorable()) {
            if (!level.isClientSide) {
                setColor(dye.getDyeColor());
                if (!player.isCreative()) stack.shrink(1);
                sync();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    public void setColor(DyeColor dye) {
        color = dye.getTextureDiffuseColor() & 0xFFFFFF;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(item);
    }

    @Override
    public void kill() {
        if (!level().isClientSide && itemCount > 0)
            spawn(new ItemStack(item, itemCount), mainBox != null ? mainBox.getCenter().add(position()) : position());
        itemCount = 0;
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!reason.shouldDestroy() || !(level() instanceof ServerLevel sl)) return;
        dropCores();
        for (WireEnd e : new WireEnd[] {end1, end2})
            if (e instanceof WireEnd.Junction j) JunctionData.get(sl).detach(sl, j.id(), getUUID());
    }

    // ---------------------------------------------------------------- сохранение и синхронизация

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HOT, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Item")));
        setItem(it, tag.getInt("Count"));
        color = tag.contains("Color") ? tag.getInt("Color") : -1;
        segments.clear();
        for (Tag t : tag.getList("Segments", Tag.TAG_COMPOUND)) segments.add(new Point((CompoundTag) t));
        end1 = WireEnd.load(tag.getCompound("End1"));
        end2 = WireEnd.load(tag.getCompound("End2"));
        cores.clear();
        for (Tag t : tag.getList("Cores", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            cores.add(new InWorldNode[] {node(c.getCompound("A")), node(c.getCompound("B"))});
        }
        bake();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString());
        tag.putInt("Count", itemCount);
        if (color >= 0) tag.putInt("Color", color);
        ListTag segs = new ListTag();
        for (Point s : segments) segs.add(s.save());
        tag.put("Segments", segs);
        if (end1 != null) tag.put("End1", end1.save());
        if (end2 != null) tag.put("End2", end2.save());
        ListTag cs = new ListTag();
        for (InWorldNode[] c : cores) {
            CompoundTag t = new CompoundTag();
            t.put("A", node(c[0]));
            t.put("B", node(c[1]));
            cs.add(t);
        }
        tag.put("Cores", cs);
    }

    static CompoundTag node(InWorldNode n) {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", n.id());
        t.putLong("Pos", n.sourcePos().asLong());
        return t;
    }

    static InWorldNode node(CompoundTag t) {
        return new InWorldNode(t.getInt("Id"), BlockPos.of(t.getLong("Pos")));
    }

    /** Данные для клиента: без жил (они только на сервере) */
    CompoundTag clientData() {
        CompoundTag tag = new CompoundTag();
        addAdditionalSaveData(tag);
        tag.remove("Cores");
        tag.putDouble("X", getX());
        tag.putDouble("Y", getY());
        tag.putDouble("Z", getZ());
        return tag;
    }

    public void readClientData(CompoundTag tag) {
        setPosRaw(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        readAdditionalSaveData(tag);
        setOldPosAndRot();
    }

    /** Разослать изменения тем, кто видит сущность (как EntityDataS2CPacket Power Grid) */
    public void sync() {
        if (level() instanceof ServerLevel && !isRemoved())
            PacketDistributor.sendToPlayersTrackingEntity(this, new WirePackets.Sync(getId(), clientData()));
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeNbt(clientData());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) readClientData(tag);
    }

    /** Ближайшее попадание луча start → end в рамки отрезков, для наведения (ComplexEntityRaycast Power Grid) */
    public Vec3 raycast(Vec3 start, Vec3 end) {
        Vec3 local = position();
        Vec3 a = start.subtract(local), b = end.subtract(local);
        double best = b.distanceToSqr(a);
        Vec3 hit = null;
        for (AABB box : boxes) {
            Optional<Vec3> h = box.clip(a, b);
            if (h.isEmpty()) continue;
            double d = h.get().distanceToSqr(a);
            if (d < best) {
                best = d;
                hit = h.get().add(local);
            }
        }
        return hit;
    }
}
