package stress.test;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

/**
 * Память и сборщик мусора. Каждая пауза сборщика — в счётчики по «сборщик / причина», пауза от 200 мс — сразу строкой
 * «[STRESS] GC» (было → стало по кучe и старому поколению). /stress heap <метка> — полная сборка через гистограмму живых
 * объектов (как jcmd GC.class_histogram): живой объём кучи, верх классов, классы мира (утечки миров и чанков), память
 * вне кучи (Metaspace, код, прямые буферы, RSS и своп процесса), сводка пауз с прошлого замера.
 */
final class Memory {
    /** Классы, по числу экземпляров которых видно утечку: миры, чанки, держатели чанков, рендер-секции */
    static final Pattern WATCH = Pattern.compile(
            "^(net\\.minecraft\\.(server\\.level\\.ServerLevel|client\\.multiplayer\\.ClientLevel|world\\.level\\.chunk\\."
                    + "(LevelChunk|ProtoChunk|LevelChunkSection|ImposterProtoChunk)|server\\.level\\.ChunkHolder|world\\.entity\\."
                    + "Entity)|.*\\.RenderSection)$");

    /** «сборщик / причина / действие» → {пауз, мс всего, мс макс} */
    static final Map<String, long[]> stats = new TreeMap<>();
    static volatile boolean skipTick;

    static void init() {
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans())
            if (b instanceof NotificationEmitter em)
                em.addNotificationListener((n, h) -> {
                    if (n.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION))
                        onGc(GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData()));
                }, null, null);
        StressTest.LOG.info("[STRESS] JVM: {}", flags());
    }

    static void onGc(GarbageCollectionNotificationInfo i) {
        GcInfo g = i.getGcInfo();
        long ms = g.getDuration();
        // «G1 Concurrent GC» — фоновая разметка, не пауза потоков игры
        boolean concurrent = i.getGcName().contains("Concurrent");
        String key = i.getGcName() + " / " + i.getGcCause() + " / " + i.getGcAction();
        synchronized (stats) {
            long[] s = stats.computeIfAbsent(key, k -> new long[3]);
            s[0]++;
            s[1] += ms;
            s[2] = Math.max(s[2], ms);
        }
        if (concurrent || ms < 200) return;
        StressTest.LOG.info("[STRESS] GC {} мс: {}; куча {} → {} МБ, старое поколение {} → {} МБ, выделено {} МБ", ms, key,
                mb(sum(g.getMemoryUsageBeforeGc())), mb(sum(g.getMemoryUsageAfterGc())),
                mb(old(g.getMemoryUsageBeforeGc())), mb(old(g.getMemoryUsageAfterGc())), mb(committed(g.getMemoryUsageAfterGc())));
    }

    static long sum(Map<String, MemoryUsage> m) {
        long r = 0;
        for (Map.Entry<String, MemoryUsage> e : m.entrySet()) if (heapPool(e.getKey())) r += e.getValue().getUsed();
        return r;
    }

    static long committed(Map<String, MemoryUsage> m) {
        long r = 0;
        for (Map.Entry<String, MemoryUsage> e : m.entrySet()) if (heapPool(e.getKey())) r += e.getValue().getCommitted();
        return r;
    }

    static long old(Map<String, MemoryUsage> m) {
        long r = 0;
        for (Map.Entry<String, MemoryUsage> e : m.entrySet()) if (e.getKey().contains("Old")) r += e.getValue().getUsed();
        return r;
    }

    static boolean heapPool(String name) {
        return name.contains("Eden") || name.contains("Survivor") || name.contains("Old");
    }

    static String flags() {
        StringBuilder sb = new StringBuilder();
        HotSpotDiagnosticMXBean hs = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        for (String f : new String[] {"UseG1GC", "UseZGC", "ZGenerational", "UseShenandoahGC", "UseParallelGC", "MaxHeapSize",
                "InitialHeapSize", "G1HeapRegionSize", "MaxGCPauseMillis", "InitiatingHeapOccupancyPercent", "G1ReservePercent",
                "ParallelGCThreads", "ConcGCThreads", "DisableExplicitGC", "UseStringDeduplication", "MaxDirectMemorySize",
                "AlwaysPreTouch", "UseLargePages"})
            try {
                sb.append(f).append('=').append(hs.getVMOption(f).getValue()).append(' ');
            } catch (IllegalArgumentException ignored) {
            }
        sb.append("CPU=").append(Runtime.getRuntime().availableProcessors());
        sb.append("; аргументы: ").append(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith("-X") || a.startsWith("-XX")).toList()));
        return sb.toString();
    }

    /** Полная сборка + гистограмма живых объектов; пишет итог в журнал, возвращает короткую строку для чата */
    static String heap(String label) {
        long t0 = System.nanoTime();
        String histo;
        try {
            histo = (String) ManagementFactory.getPlatformMBeanServer().invoke(
                    new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
                    new Object[] {new String[0]}, new String[] {String[].class.getName()});
        } catch (Exception ex) {
            StressTest.LOG.error("[STRESS] гистограмма не снята", ex);
            return "ошибка: " + ex;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        skipTick = true;
        long live = 0, objects = 0;
        List<String> top = new ArrayList<>(), watch = new ArrayList<>();
        for (String line : histo.split("\n")) {
            String[] w = line.strip().split("\\s+");
            if (w.length >= 3 && w[0].equals("Total")) {
                objects = Long.parseLong(w[1]);
                live = Long.parseLong(w[2]);
            } else if (w.length >= 4 && w[0].endsWith(":")) {
                String row = String.format("%,d МБ %,d шт %s", Long.parseLong(w[2]) >> 20, Long.parseLong(w[1]), w[3]);
                if (top.size() < 30) top.add(row);
                if (WATCH.matcher(w[3]).matches()) watch.add(row);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[STRESS] ПАМЯТЬ «%s»: живых объектов %,d МБ (%,d шт), полная сборка %d мс; %s", label, live >> 20,
                objects, ms, offHeap()));
        sb.append("\n  классы мира: ").append(watch.isEmpty() ? "—" : String.join("; ", watch));
        sb.append("\n  верх по объёму:");
        for (String r : top) sb.append("\n    ").append(r);
        sb.append("\n  паузы GC с прошлого замера:");
        synchronized (stats) {
            if (stats.isEmpty()) sb.append(" нет");
            for (Map.Entry<String, long[]> e : stats.entrySet())
                sb.append(String.format("\n    %s: %d шт, %d мс, макс %d мс", e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2]));
            stats.clear();
        }
        StressTest.LOG.info(sb.toString());
        return String.format("живых %d МБ, сборка %d мс", live >> 20, ms);
    }

    static String offHeap() {
        StringBuilder sb = new StringBuilder();
        MemoryUsage h = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        sb.append(String.format("куча занято %d / выделено %d / макс %d МБ", mb(h.getUsed()), mb(h.getCommitted()), mb(h.getMax())));
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
            if (!heapPool(p.getName())) sb.append(String.format(", %s %d МБ", p.getName(), mb(p.getUsage().getUsed())));
        for (BufferPoolMXBean b : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))
            sb.append(String.format(", буферы %s %d МБ (%d)", b.getName(), mb(b.getMemoryUsed()), b.getCount()));
        try {
            for (String l : Files.readAllLines(Path.of("/proc/self/status")))
                if (l.startsWith("VmRSS") || l.startsWith("VmSwap")) sb.append(", ").append(l.replaceAll("\\s+", " "));
        } catch (IOException ignored) {
            // не Linux
        }
        return sb.toString();
    }

    static long mb(long bytes) {
        return bytes >> 20;
    }

    private Memory() {}
}
