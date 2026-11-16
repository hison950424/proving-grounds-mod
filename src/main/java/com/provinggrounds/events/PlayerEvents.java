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

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (GameManager.get().isInGame()) {
            GameManager.get().removePlayer(player);
        }
        ProvingGroundsMod.LOGGER.info("Player {} logged out.", player.getName().getString());
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!GameManager.get().isInGame()) return;

        // 記錄死亡
        PlayerDataManager.get().addDeath(victim);

        // 記錄擊殺方 K
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != victim) {
            PlayerDataManager.get().addKill(killer);
            ProvingGroundsMod.LOGGER.debug("{} killed {}", killer.getName().getString(), victim.getName().getString());
        }
    }
}
