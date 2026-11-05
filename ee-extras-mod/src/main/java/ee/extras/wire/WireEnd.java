package ee.extras.wire;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import ee.extras.cable.CableEnd;
import ee.extras.cable.CableKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Конец провода. Подключаемые — прибор (BLOCK) и узел ответвления (JUNCTION): у них есть узлы EE, по одному на жилу.
 * Остальные живут только во время укладки: край провода (WIRE), точка на поверхности (FREE), точка на середине
 * провода, которая станет узлом (DEFERRED).
 *
 * Перенесено из Create: Power Grid 0.6.2 (IWireEndpoint, WireEndpointType, BlockWireEndpoint, JunctionWireEndpoint,
 * BlockWireEntityEndpoint, ImaginaryWireEndpoint, DeferredJunctionWireEndpoint; Apache-2.0, см. NOTICE). Изменения:
 * один файл; прибор — блок, принимающий двухжильный кабель (CableEnd), а не IElectric; узел ответвления — узлы EE из
 * JunctionData вместо узла сети Power Grid в памяти; без Sable и сдвигов при сборке машин.
 */
public sealed interface WireEnd {
    enum Type {
        BLOCK(true), JUNCTION(true), WIRE(false), FREE(false), DEFERRED(true);

        public final boolean connectable;

        Type(boolean connectable) {
            this.connectable = connectable;
        }
    }

    Type type();

    void write(CompoundTag tag);

    Vec3 position(Level level);

    /** Конец на месте: прибор стоит, узел есть. Для неподключаемых — всегда да */
    default boolean valid(Level level) {
        return true;
    }

    /** Узлы EE жил 1…n; null — нет (не подключаемый конец или прибор пропал) */
    default List<InWorldNode> nodes(ServerLevel level, int conductors) {
        return null;
    }

    default CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type().name());
        write(tag);
        return tag;
    }

    static WireEnd load(CompoundTag tag) {
        if (tag == null || !tag.contains("Type")) return null;
        try {
            return switch (Type.valueOf(tag.getString("Type"))) {
                case BLOCK -> new Block(BlockPos.of(tag.getLong("Pos")));
                case JUNCTION -> new Junction(tag.getUUID("Id"), new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")));
                case WIRE -> new WireTip(tag.getUUID("Id"), BlockPos.of(tag.getLong("Pos")), tag.getBoolean("End"));
                case FREE -> new Free(new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")));
                case DEFERRED -> new Deferred(tag.getUUID("Id"), BlockPos.of(tag.getLong("Pos")), tag.getInt("Index"), tag.getInt("Point"));
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static SurfaceWireEntity find(Level level, BlockPos near, UUID id) {
        if (level instanceof ServerLevel sl && sl.getEntity(id) instanceof SurfaceWireEntity w) return w;
        for (SurfaceWireEntity w : level.getEntitiesOfClass(SurfaceWireEntity.class, new AABB(near).inflate(1), e -> e.getUUID().equals(id)))
            return w;
        return null;
    }

    /** Прибор, принимающий двухжильный кабель: провод подходит к точке кабеля (узел CableEnd.cable) */
    record Block(BlockPos pos) implements WireEnd {
        @Override
        public Type type() {
            return Type.BLOCK;
        }

        @Override
        public void write(CompoundTag tag) {
            tag.putLong("Pos", pos.asLong());
        }

        public CableEnd end(Level level) {
            if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) return null;
            CableEnd end = CableEnd.of(level, pos, level.getBlockState(pos));
            return end != null && end.kind() == CableKind.DUPLEX ? end : null;
        }

        public boolean loaded(Level level) {
            return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        }

        @Override
        public Vec3 position(Level level) {
            CableEnd end = end(level);
            BlockState state = level.getBlockState(pos);
            if (end != null && state.getBlock() instanceof ElectricalDeviceBlock<?> b) {
                Vec3 local = b.getNodePosition(level, pos, state, end.cable().id());
                if (local != null) return Vec3.atLowerCornerOf(pos).add(local);
            }
            return Vec3.atCenterOf(pos);
        }

        @Override
        public boolean valid(Level level) {
            return end(level) != null;
        }

        @Override
        public List<InWorldNode> nodes(ServerLevel level, int conductors) {
            CableEnd end = end(level);
            if (end == null) return null;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ElectricalDeviceBlock<?> b) b.ensureNodesExist(level, pos, state);
            List<InWorldNode> out = new ArrayList<>();
            for (int k = 1; k <= conductors; k++) out.add(end.conductor(k));
            return out;
        }
    }

    /** Узел ответвления: узлы EE без блока (JunctionData) */
    record Junction(UUID id, Vec3 pos) implements WireEnd {
        @Override
        public Type type() {
            return Type.JUNCTION;
        }

        @Override
        public void write(CompoundTag tag) {
            tag.putUUID("Id", id);
            tag.putDouble("X", pos.x);
            tag.putDouble("Y", pos.y);
            tag.putDouble("Z", pos.z);
        }

        @Override
        public Vec3 position(Level level) {
            return pos;
        }

        @Override
        public boolean valid(Level level) {
            return !(level instanceof ServerLevel sl) || JunctionData.get(sl).exists(id);
        }

        @Override
        public List<InWorldNode> nodes(ServerLevel level, int conductors) {
            List<InWorldNode> nodes = JunctionData.get(level).nodes(id);
            return nodes != null && nodes.size() >= conductors ? nodes : null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Junction j && j.id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    /** Край провода id: end — конец (после последнего отрезка), иначе начало */
    record WireTip(UUID id, BlockPos near, boolean end) implements WireEnd {
        public WireTip(SurfaceWireEntity wire, boolean end) {
            this(wire.getUUID(), wire.blockPosition(), end);
        }

        @Override
        public Type type() {
            return Type.WIRE;
        }

        @Override
        public void write(CompoundTag tag) {
            tag.putUUID("Id", id);
            tag.putLong("Pos", near.asLong());
            tag.putBoolean("End", end);
        }

        public SurfaceWireEntity wire(Level level) {
            return find(level, near, id);
        }

        @Override
        public Vec3 position(Level level) {
            SurfaceWireEntity w = wire(level);
            if (w == null) return Vec3.atCenterOf(near);
            return end ? w.endPosition() : w.position();
        }
    }

    /** Точка, куда кликнули по поверхности: свободный край */
    record Free(Vec3 pos) implements WireEnd {
        @Override
        public Type type() {
            return Type.FREE;
        }

        @Override
        public void write(CompoundTag tag) {
            tag.putDouble("X", pos.x);
            tag.putDouble("Y", pos.y);
            tag.putDouble("Z", pos.z);
        }

        @Override
        public Vec3 position(Level level) {
            return pos;
        }
    }

    /** Точка point (в шагах сетки) отрезка index провода id: при подключении провод делится и там встаёт узел */
    record Deferred(UUID id, BlockPos near, int index, int point) implements WireEnd {
        @Override
        public Type type() {
            return Type.DEFERRED;
        }

        @Override
        public void write(CompoundTag tag) {
            tag.putUUID("Id", id);
            tag.putLong("Pos", near.asLong());
            tag.putInt("Index", index);
            tag.putInt("Point", point);
        }

        @Override
        public Vec3 position(Level level) {
            SurfaceWireEntity w = find(level, near, id);
            if (w == null || w.segments.isEmpty()) return Vec3.atCenterOf(near);
            Point s = w.segments.get(Math.min(index, w.segments.size() - 1));
            return s.start.relative(s.direction, (double) point / Point.GRID);
        }

        /** Разделить провод и вернуть узел; null — провода нет */
        public Junction resolve(ServerLevel level) {
            SurfaceWireEntity w = find(level, near, id);
            return w == null ? null : w.split(index, point);
        }
    }

    static boolean same(WireEnd a, WireEnd b) {
        return Objects.equals(a, b);
    }
}
