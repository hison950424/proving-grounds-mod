package com.provinggrounds.events;

import com.provinggrounds.core.GameManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class WorldEvents {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        GameManager.get().setServer(server);
        // 崩潰恢復：若 SavedData 顯示有未完成遊戲，重置回大廳
        GameManager.get().reset();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 伺服器關閉時乾淨重置（下次啟動從大廳開始）
        GameManager.get().reset();
    }

    /** 每 tick 鉤子。遊戲模式邏輯在這裡分派。 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!GameManager.get().isInGame()) return;

        // 各模式 tick 邏輯（之後各 Mode 類實作）
        switch (GameManager.get().getMode()) {
            case BATTLE_ROYALE -> tickBR();
            case TDM           -> tickTDM();
            case DOM           -> tickDOM();
            case GK            -> tickGK();
            default            -> {}
        }
    }

    private void tickBR()  { /* BattleRoyaleMode.get().tick() */ }
    private void tickTDM() { /* TDMMode.get().tick() */ }
    private void tickDOM() { /* DOMMode.get().tick() */ }
    private void tickGK()  { /* GKMode.get().tick() */ }
}
