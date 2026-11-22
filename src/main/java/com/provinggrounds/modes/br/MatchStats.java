package com.provinggrounds.modes.br;

/**
 * 檔案: MatchStats.java
 * 目的: 一位參賽者的本場統計（計畫書 B4）。在 BattleRoyaleMode 既有的處理點累計：
 *   造成傷害（onDamaged）、倒地（down）、擊殺／助攻／死亡（eliminate）、扶起（revive）、祭壇復活（activateAltar）、
 *   XP（addXp 的地方）。存活時間從飛機起飛算起，淘汰時停、祭壇復活後繼續算。
 */
public final class MatchStats {
    public int kills, assists, deaths, downs, revives, altarRevives, xp;
    public float damage;
    public int aliveTicks;
    public int aliveSince = -1;      // 目前這段存活的起點 tick；-1 = 不在存活中
    public int lastDeathTick = -1;   // 最後一次淘汰的 tick（排名用）

    public void startAlive(int tick) {
        if (aliveSince < 0) aliveSince = tick;
    }

    public void stopAlive(int tick) {
        if (aliveSince >= 0) aliveTicks += tick - aliveSince;
        aliveSince = -1;
    }
}
