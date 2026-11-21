package com.provinggrounds.client;

import com.provinggrounds.network.MapImagePacket;
import com.provinggrounds.network.MapMarkersPacket;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 檔案: ClientMapData.java
 * 目的: 客戶端收到的地圖資料（純資料，不碰繪圖，專用伺服器載入也不會崩潰）：
 *       俯視圖像素（收齊分段後解壓）、地圖標記。繪圖用的材質由 ui/MapTextures 依 version 建立。
 */
public final class ClientMapData {

    /** 解壓後的地圖圖片：pixels 是原版地圖顏色編號（size × size，一列一列從北到南）。 */
    public record Image(int mapId, long version, int minX, int minZ, int size, int scale, byte[] pixels) {}

    @Nullable private static volatile Image image;
    private static volatile List<MapMarkersPacket.Marker> markers = List.of();
    private static volatile long markersAt = 0;

    // 分段組裝中
    private static long pendingVersion = -1;
    private static int pendingMap = -1;
    @Nullable private static byte[][] parts;

    private ClientMapData() {}

    public static synchronized void receivePart(MapImagePacket p) {
        if (p.size() <= 0) { // 這張地圖沒有俯視圖：清掉舊的
            image = null;
            parts = null;
            return;
        }
        Image cur = image;
        if (cur != null && cur.mapId() == p.mapId() && cur.version() == p.version()) return; // 已經有這張
        if (parts == null || pendingVersion != p.version() || pendingMap != p.mapId() || parts.length != p.totalParts()) {
            parts = new byte[p.totalParts()][];
            pendingVersion = p.version();
            pendingMap = p.mapId();
        }
        if (p.part() < 0 || p.part() >= parts.length) return;
        parts[p.part()] = p.bytes();
        for (byte[] b : parts) if (b == null) return; // 還沒收齊

        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] b : parts) all.writeBytes(b);
        parts = null;
        byte[] pixels = new byte[p.size() * p.size()];
        Inflater inf = new Inflater();
        try {
            inf.setInput(all.toByteArray());
            int off = 0;
            while (off < pixels.length && !inf.finished()) {
                int n = inf.inflate(pixels, off, pixels.length - off);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                off += n;
            }
        } catch (DataFormatException e) {
            return;
        } finally {
            inf.end();
        }
        image = new Image(p.mapId(), p.version(), p.minX(), p.minZ(), p.size(), p.scale(), pixels);
    }

    public static void setMarkers(MapMarkersPacket p) {
        markers = p.markers();
        markersAt = System.currentTimeMillis();
    }

    @Nullable
    public static Image image() { return image; }

    /** 目前的標記（超過 2 秒沒更新就當作沒有，例如已離開遊戲）。 */
    public static List<MapMarkersPacket.Marker> markers() {
        return System.currentTimeMillis() - markersAt > 2000 ? List.of() : markers;
    }

    public static synchronized void clear() {
        image = null;
        parts = null;
        markers = List.of();
        markersAt = 0;
    }
}
