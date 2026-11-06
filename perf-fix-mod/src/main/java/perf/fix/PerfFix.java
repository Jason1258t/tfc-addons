package perf.fix;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(PerfFix.MOD_ID)
public final class PerfFix {
    public static final String MOD_ID = "perf_fix";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PerfFix() {
        if (ModList.get().isLoaded("jmsharedmaps") && TileWriter.ENABLED) {
            LOGGER.info("Perf Fix: тайлы общих карт JourneyMap пишутся в фоновом потоке");
            // Мир закрывается (у хоста — выход в меню): дописать очередь, пока папка мира на месте
            NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> TileWriter.drain(30));
        }
    }
}
