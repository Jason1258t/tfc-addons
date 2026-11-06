package perf.fix.mixin;

import com.jmsharedmaps.server.RegionTileStore;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Замки файлов регионов: под ними же мод при сохранении мира пересобирает файл (writeRegionCompact). */
@Pseudo
@Mixin(value = RegionTileStore.class, remap = false)
public interface RegionTileStoreAccessor {
    @Accessor("LOCKS")
    static ConcurrentHashMap<String, Object> perfFix$getLocks() {
        throw new AssertionError();
    }
}
