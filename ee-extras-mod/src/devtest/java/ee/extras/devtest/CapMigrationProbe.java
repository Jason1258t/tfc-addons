package ee.extras.devtest;

import ee.extras.cable.CableCap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

final class CapMigrationProbe {
    static void check(ServerLevel level, BlockPos pos) {
        try {
            var tag = new CompoundTag();
            tag.putLongArray("Caps", new long[] {pos.asLong()});
            var load =
                    CableCap.class.getDeclaredMethod(
                            "load", CompoundTag.class, HolderLookup.Provider.class);
            load.setAccessible(true);
            var old = (CableCap) load.invoke(null, tag, level.registryAccess());
            var migrate = CableCap.class.getDeclaredMethod("migrate", ServerLevel.class);
            migrate.setAccessible(true);
            migrate.invoke(old, level);
            var saved = old.save(new CompoundTag(), level.registryAccess());
            if (!old.has(pos)
                    || saved.getLongArray("Caps").length != 0
                    || saved.getList("Pairs", 10).size() != 1)
                throw new IllegalStateException("ПАРЫ FAIL: миграция старой крышки");
            var reload = (CableCap) load.invoke(null, saved, level.registryAccess());
            if (!reload.save(new CompoundTag(), level.registryAccess()).equals(saved))
                throw new IllegalStateException("ПАРЫ FAIL: повторное чтение пар");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private CapMigrationProbe() {}
}
