package com.provinggrounds.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 檔案: PlayerStatsMigrationTest.java
 * 目的: 確認舊存檔（沒有分模式資料）第一次載入時，總數字完整搬進大逃殺的 ModeStats，
 *       而且存檔後再讀回不會重複搬、不會遺失（計畫書 B5）。
 */
class PlayerStatsMigrationTest {

    private static CompoundTag oldFormat() {
        CompoundTag t = new CompoundTag();
        t.putInt("kills", 12);
        t.putInt("deaths", 5);
        t.putInt("assists", 7);
        t.putInt("wins", 3);
        t.putLong("xp", 1234);
        t.putString("name", "Tester");
        return t;
    }

    @Test
    void oldTotalsMoveIntoBattleRoyale() {
        PlayerDataManager.PlayerStats s = PlayerDataManager.PlayerStats.load(oldFormat());
        PlayerDataManager.ModeStats br = s.modes.get(PlayerDataManager.MODE_BR);
        assertEquals(12, br.kills);
        assertEquals(5, br.deaths);
        assertEquals(7, br.assists);
        assertEquals(3, br.wins);
        // 總數字本身保留
        assertEquals(12, s.kills);
        assertEquals(1234, s.xp);
        assertEquals("Tester", s.lastName);
    }

    @Test
    void saveAndReloadKeepsModeStats() {
        PlayerDataManager.PlayerStats s = PlayerDataManager.PlayerStats.load(oldFormat());
        s.mode(PlayerDataManager.MODE_BR).games = 9;
        s.mode(PlayerDataManager.MODE_BR).damage = 456.5;
        PlayerDataManager.PlayerStats again = PlayerDataManager.PlayerStats.load(s.save());
        PlayerDataManager.ModeStats br = again.modes.get(PlayerDataManager.MODE_BR);
        assertEquals(9, br.games);
        assertEquals(12, br.kills);
        assertEquals(456.5, br.damage, 1e-9);
        assertEquals(1, again.modes.size());
    }
}
