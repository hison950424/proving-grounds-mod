package com.provinggrounds.venue;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 檔案: Venue.java
 * 目的: 一個場地（大廳、訓練場、迷你死鬥、跑庫塔…）。資料來自伺服器設定 [venues].list，一行一個場地：
 *   「ID|名稱|旗標|x y z; x y z; …」（多個座標時隨機選一個）
 *   旗標（逗號分隔）：
 *     safe  安全區：玩家之間打不到（大廳、訓練場）
 *     pvp   可以互相攻擊（迷你死鬥）
 *     ammo  進場給 TACZ 無限彈藥箱
 *     clear 進場清掉槍、彈藥箱、配件（跑庫塔）
 *     clear_all 進場清掉槍、彈藥箱、配件、補品、投擲物、近戰武器（大廳）
 *     respawn 在這裡死掉就在這裡隨機重生
 *     hidden  不顯示在場地選擇選單
 */
public record Venue(String id, String name, Set<String> flags, List<Vec3> points) {

    public boolean has(String flag) { return flags.contains(flag); }

    @Nullable
    public static Venue parse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 4) return null;
        String id = parts[0].trim().toLowerCase(Locale.ROOT);
        String name = parts[1].trim();
        Set<String> flags = new java.util.HashSet<>();
        for (String f : parts[2].split(",")) if (!f.isBlank()) flags.add(f.trim().toLowerCase(Locale.ROOT));
        List<Vec3> pts = new ArrayList<>();
        for (String p : parts[3].split(";")) {
            String[] xyz = p.trim().split("\\s+");
            if (xyz.length < 3) continue;
            try {
                pts.add(new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2])));
            } catch (NumberFormatException ignored) {
                // 格式錯誤的座標略過
            }
        }
        if (id.isEmpty() || pts.isEmpty()) return null;
        return new Venue(id, name, Set.copyOf(flags), List.copyOf(pts));
    }
}
