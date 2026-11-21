package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * 檔案: MapImageStore.java
 * 目的: 地圖俯視圖的存放（伺服器端）。由 /pg map render 產生（MapRenderJob），
 *       存在 world/provinggrounds/map_<編號>.bin，開局時傳給玩家畫大地圖與小地圖。
 *   圖片格式：每格 1 byte，是原版地圖顏色編號（顏色 id × 4 + 明暗），跟原版地圖物品同一套顏色。
 *   檔案內容：版本號、左上角世界座標、邊長（格數）、每格代表幾個方塊、壓縮後的像素資料。
 */
public final class MapImageStore {

    /** 一張地圖圖片。data 是 deflate 壓縮後的像素（size × size bytes）。 */
    public record MapImage(int mapId, long version, int minX, int minZ, int size, int scale, byte[] data) {}

    private static final int MAGIC = 0x50474D31; // "PGM1"
    private static final Map<Integer, MapImage> CACHE = new HashMap<>();

    private MapImageStore() {}

    private static Path file(MinecraftServer server, int mapId) {
        return server.getWorldPath(LevelResource.ROOT).resolve("provinggrounds").resolve("map_" + mapId + ".bin");
    }

    /** 取得地圖圖片；還沒產生過回傳 null。 */
    @Nullable
    public static synchronized MapImage get(MinecraftServer server, int mapId) {
        MapImage cached = CACHE.get(mapId);
        if (cached != null) return cached;
        Path f = file(server, mapId);
        if (!Files.exists(f)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            if (in.readInt() != MAGIC) return null;
            long version = in.readLong();
            int minX = in.readInt(), minZ = in.readInt(), size = in.readInt(), scale = in.readInt();
            byte[] data = new byte[in.readInt()];
            in.readFully(data);
            MapImage img = new MapImage(mapId, version, minX, minZ, size, scale, data);
            CACHE.put(mapId, img);
            return img;
        } catch (IOException e) {
            ProvingGroundsMod.LOGGER.error("Failed to read map image {}", f, e);
            return null;
        }
    }

    /** 壓縮並存檔（產生完成時呼叫）。 */
    public static synchronized MapImage save(MinecraftServer server, int mapId, int minX, int minZ, int size, int scale, byte[] pixels) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(pixels.length / 4);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, new Deflater(Deflater.BEST_COMPRESSION))) {
            dos.write(pixels);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        MapImage img = new MapImage(mapId, System.currentTimeMillis(), minX, minZ, size, scale, bos.toByteArray());
        Path f = file(server, mapId);
        try {
            Files.createDirectories(f.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(f)))) {
                out.writeInt(MAGIC);
                out.writeLong(img.version());
                out.writeInt(minX); out.writeInt(minZ); out.writeInt(size); out.writeInt(scale);
                out.writeInt(img.data().length);
                out.write(img.data());
            }
        } catch (IOException e) {
            ProvingGroundsMod.LOGGER.error("Failed to save map image {}", f, e);
        }
        CACHE.put(mapId, img);
        return img;
    }

    /** 伺服器關閉時清快取（換世界時不會用到舊圖）。 */
    public static synchronized void clearCache() {
        CACHE.clear();
    }
}
