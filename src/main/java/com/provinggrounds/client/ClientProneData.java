package com.provinggrounds.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案: ClientProneData.java
 * 目的: 客戶端收到的「誰正在趴著」名單（純資料，專用伺服器載入也不會崩潰），
 *       以及每位玩家最近一次趴下／站起的時間（毫秒），用來在轉換期間放慢動畫。
 */
public final class ClientProneData {

    private static volatile Set<UUID> prone = Set.of();
    private static final Map<UUID, Long> CHANGED_AT = new HashMap<>();

    private ClientProneData() {}

    public static synchronized void set(List<UUID> list) {
        Set<UUID> next = new HashSet<>(list);
        long now = System.currentTimeMillis();
        for (UUID id : next) if (!prone.contains(id)) CHANGED_AT.put(id, now);
        for (UUID id : prone) if (!next.contains(id)) CHANGED_AT.put(id, now);
        prone = next;
    }

    public static boolean isProne(UUID id) { return prone.contains(id); }

    public static Set<UUID> all() { return prone; }

    /** 這位玩家是否在趴下／站起的轉換期間（2 秒內）。 */
    public static synchronized boolean inTransition(UUID id) {
        Long t = CHANGED_AT.get(id);
        return t != null && System.currentTimeMillis() - t < 2000;
    }

    public static synchronized void clear() {
        prone = Set.of();
        CHANGED_AT.clear();
    }
}
