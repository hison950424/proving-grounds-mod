package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.data.PlayerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PlayerEvents {

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 設定 server 參照（確保 GameManager 有 server）
        GameManager.get().setServer(player.getServer());
        // 載入玩家跨局資料
        PlayerDataManager.get().loadPlayer(player);
        ProvingGroundsMod.LOGGER.info("Player {} logged in.", player.getName().getString());
    }

    /** 大廳：觀戰者登入時維持觀察者模式、「本局結束後加入」的人回大廳。優先度 LOW：在 BR 的還原之後。 */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public void onLobbyLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.provinggrounds.core.LobbyManager.get().onLogin(player);
            com.provinggrounds.core.ProneManager.onLogin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        com.provinggrounds.core.LobbyManager.get().onLogout(player);
        com.provinggrounds.core.ProneManager.onLogout(player);
        if (GameManager.get().isInGame()) {
            GameManager.get().removePlayer(player);
        }
        ProvingGroundsMod.LOGGER.info("Player {} logged out.", player.getName().getString());
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!GameManager.get().isInGame()) return;
        // BR 的擊殺/死亡/助攻由 BattleRoyaleMode 自己記錄，避免重複計算
        if (GameManager.get().getMode() == GameManager.GameMode.BATTLE_ROYALE) return;

        // 記錄死亡
        PlayerDataManager.get().addDeath(victim);

        // 記錄擊殺方 K
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != victim) {
            PlayerDataManager.get().addKill(killer);
            ProvingGroundsMod.LOGGER.debug("{} killed {}", killer.getName().getString(), victim.getName().getString());
        }
    }
}
