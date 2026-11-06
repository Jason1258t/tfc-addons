package perf.fix;

import com.jmsharedmaps.server.RegionTileStore;
import com.jmsharedmaps.server.StoredTile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import perf.fix.mixin.RegionTileStoreAccessor;

/**
 * Запись тайлов общих карт в файлы .jmregion в одном фоновом потоке, в том же формате, что у JourneyMap Shared Maps
 * 1.1.3 (RegionTileStore): заголовок 8200 байт — MAGIC, VERSION и 1024 пары «смещение, длина»; записи дописываются в
 * конец файла — timestamp (long), длина (int), сжатый снимок. Отличия от мода: файл открывается один раз, читается
 * только заголовок, в заголовке переписывается одна ячейка, а не весь файл.
 */
public final class TileWriter {
    /** -Dperf_fix.jmsharedmaps.disabled=true — запись как у самого мода (для сравнения на сервере разработки). */
    public static final boolean ENABLED = !Boolean.getBoolean("perf_fix.jmsharedmaps.disabled");

    private static final int MAGIC = 1246581586;
    private static final int VERSION = 3;
    private static final int SLOT_COUNT = 1024;
    private static final int HEADER_SIZE = 8 + SLOT_COUNT * 8;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "perf_fix jmsharedmaps writer");
        t.setDaemon(true);
        return t;
    });
    private static final Set<Path> CREATED_DIRS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static final AtomicInteger WRITTEN = new AtomicInteger();

    private TileWriter() {
    }

    public static void submit(Path dimDir, StoredTile tile) {
        PENDING.incrementAndGet();
        EXECUTOR.execute(() -> {
            try {
                write(dimDir, tile);
                WRITTEN.incrementAndGet();
            } catch (IOException | RuntimeException e) {
                PerfFix.LOGGER.warn("Не удалось записать тайл общей карты {} {},{}", tile.dimension(), tile.chunkX(),
                        tile.chunkZ(), e);
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }

    /** Дождаться записи всего, что в очереди (остановка сервера). */
    public static void drain(long timeoutSeconds) {
        if (PENDING.get() == 0) {
            return;
        }
        try {
            EXECUTOR.submit(() -> {
            }).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            PerfFix.LOGGER.warn("Очередь тайлов общих карт не дописана за {} с, осталось {}", timeoutSeconds,
                    PENDING.get());
        }
    }

    public static int pending() {
        return PENDING.get();
    }

    public static int written() {
        return WRITTEN.get();
    }

    private static void write(Path dimDir, StoredTile tile) throws IOException {
        int rx = RegionTileStore.regionX(tile.chunkX());
        int rz = RegionTileStore.regionZ(tile.chunkZ());
        Path path = RegionTileStore.regionPath(dimDir, rx, rz);
        int slot = RegionTileStore.slotIndex(tile.chunkX(), tile.chunkZ());
        byte[] payload = tile.compressedSnapshot();
        Object lock = RegionTileStoreAccessor.perfFix$getLocks().computeIfAbsent(path.toString(), k -> new Object());
        synchronized (lock) {
            if (CREATED_DIRS.add(dimDir)) {
                Files.createDirectories(dimDir);
            }
            try (FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {
                long size = ch.size();
                if (size < HEADER_SIZE) {
                    // Новый или обрезанный файл — пустой заголовок, как newIndex() + writeHeader() у мода
                    ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
                    header.putInt(MAGIC).putInt(VERSION).rewind();
                    ch.truncate(0);
                    writeFully(ch, header, 0);
                    size = HEADER_SIZE;
                }
                ByteBuffer record = ByteBuffer.allocate(12 + payload.length).order(ByteOrder.BIG_ENDIAN);
                record.putLong(tile.timestamp()).putInt(payload.length).put(payload).flip();
                writeFully(ch, record, size);
                ByteBuffer cell = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
                cell.putInt((int) size).putInt(12 + payload.length).flip();
                writeFully(ch, cell, 8 + slot * 8L);
            }
            // Как у мода: старый файл тайла из версии до регионов больше не нужен
            Files.deleteIfExists(dimDir.resolve(tile.chunkX() + "_" + tile.chunkZ() + ".bin"));
        }
    }

    private static void writeFully(FileChannel ch, ByteBuffer buf, long position) throws IOException {
        while (buf.hasRemaining()) {
            position += ch.write(buf, position);
        }
    }
}
