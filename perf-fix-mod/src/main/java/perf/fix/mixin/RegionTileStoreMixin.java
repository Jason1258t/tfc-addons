package perf.fix.mixin;

import com.jmsharedmaps.server.RegionTileStore;
import com.jmsharedmaps.server.StoredTile;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perf.fix.TileWriter;

/**
 * JourneyMap Shared Maps 1.1.3 пишет каждый присланный тайл карты прямо в главном потоке сервера и при этом читает
 * весь файл региона ради заголовка 8 КБ. Запись уходит в отдельный поток {@link TileWriter}; тайл к этому моменту уже
 * лежит в памяти мода (ServerTileCache), так что игроки и пересылка карт ничего не замечают.
 */
@Pseudo
@Mixin(value = RegionTileStore.class, remap = false)
public abstract class RegionTileStoreMixin {
    @Inject(method = "writeTile", at = @At("HEAD"), cancellable = true, remap = false)
    private static void perfFix$writeInBackground(Path dimDir, StoredTile tile, CallbackInfo ci) {
        if (TileWriter.ENABLED) {
            TileWriter.submit(dimDir, tile);
            ci.cancel();
        }
    }
}
