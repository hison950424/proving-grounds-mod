package com.provinggrounds.events;

import com.provinggrounds.block.AltarBlockEntity;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.data.MarkerScanJob;
import com.provinggrounds.data.PlayerDataManager;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 檔案: WorldEvents.java
 * 目的: 伺服器生命週期（啟動、關閉）與每 tick 分派。
 */
public class WorldEvents {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        GameManager.get().setServer(server);
        // 讀取跨局玩家資料（world/data/provinggrounds_players.dat）
        PlayerDataManager.get().attach(server);
        // 崩潰恢復：一律從大廳開始；參賽者重新登入時依 pg_br 標記還原
        GameManager.get().reset();
        // 彈藥分類檢查（槍械包有沒有放、幾把槍已分類）
        for (String line : com.provinggrounds.compat.AmmoCategories.report()) com.provinggrounds.ProvingGroundsMod.LOGGER.info(line);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 伺服器關閉：還原線上玩家並重置（箱子留給下一局開局清理）
        BattleRoyaleMode.get().forceStop(false);
        GameManager.get().reset();
        MarkerScanJob.reset();
        com.provinggrounds.data.MapRenderJob.reset();
        com.provinggrounds.data.MapRelightJob.reset();
        com.provinggrounds.data.BRMapGenerator.reset();
        com.provinggrounds.modes.arena.ArenaMapGenerator.reset();
        com.provinggrounds.data.MapImageStore.clearCache();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        PlayerDataManager.get().detach();
        com.provinggrounds.core.DisconnectBodies.onServerStopped();
        AltarBlockEntity.LOADED.clear(); // 單人模式換世界時不留舊座標
        com.provinggrounds.modes.br.StatsBoard.clear();
        com.provinggrounds.admin.SpawnPointView.clear();
    }

    /** 每 tick 鉤子。遊戲模式邏輯在這裡分派（結算 END 狀態也要繼續 tick，才能清理與還原）。 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MarkerScanJob.tickCurrent(); // 舊箱子 marker 匯入／刪除（只在大廳啟動）
        com.provinggrounds.data.MapRenderJob.tickCurrent(); // 產生地圖俯視圖（只在大廳啟動）
        com.provinggrounds.data.MapRelightJob.tickCurrent(); // 大逃殺地圖光照重建（只在大廳或建圖中啟動）
        com.provinggrounds.data.BRMapGenerator.tickCurrent(); // 程序化大逃殺地圖生成（只在大廳啟動）
        com.provinggrounds.modes.arena.ArenaMapGenerator.tickCurrent(); // 團隊室內地圖生成（只在大廳啟動）
        com.provinggrounds.core.LobbyManager.get().tick(event.getServer()); // 大廳：同步選單、強制開始倒數
        com.provinggrounds.core.ProneManager.tick(event.getServer()); // 趴下：自動解除、同步名單
        if (event.getServer().getTickCount() % 20 == 0) keepLobbyFed(event.getServer());
        if (GameManager.get().isInLobby()) return;

        switch (GameManager.get().getMode()) {
            case BATTLE_ROYALE -> BattleRoyaleMode.get().tick();
            case TDM, DOM, GK  -> com.provinggrounds.modes.arena.ArenaManager.tick();
            default            -> {}
        }
    }

    /**
     * 大廳沒有飽食度機制：不在 BR 對局中的玩家每秒補滿飢餓度，可以一直跑（飢餓條在客戶端也不畫）。
     * BR 參賽者由 BattleRoyaleMode.tickFood 處理（固定飢餓度、不自然回血）。
     */
    private static void keepLobbyFed(net.minecraft.server.MinecraftServer server) {
        BattleRoyaleMode br = BattleRoyaleMode.get();
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (br.isAliveParticipant(p.getUUID())) continue;
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5f);
        }
    }

}
