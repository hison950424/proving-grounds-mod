package com.provinggrounds.network;

import com.provinggrounds.core.GameManager;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.data.PlayerDataManager;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: MenuRequestPacket.java
 * 目的: 客戶端 → 伺服器。玩家打開主選單時送出，伺服器回傳 {@link MenuDataPacket}
 *       （戰績、地圖清單、是否為管理員、目前遊戲狀態）。
 */
public record MenuRequestPacket() {

    public void encode(FriendlyByteBuf buf) {}

    public static MenuRequestPacket decode(FriendlyByteBuf buf) {
        return new MenuRequestPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PGNetwork.sendTo(player, build(player));
            PGNetwork.sendTo(player, com.provinggrounds.core.LobbyManager.get().buildPacket(player.server));
            com.provinggrounds.core.LobbyManager.get().sendLobbyMapImage(player);
        });
        ctx.get().setPacketHandled(true);
    }

    static MenuDataPacket build(ServerPlayer player) {
        PlayerDataManager.PlayerStats s = PlayerDataManager.get().getStats(player);
        int level = s.getLevel();
        List<MenuDataPacket.MapEntry> maps = new ArrayList<>();
        for (MapRegistry.BRMap m : MapRegistry.get(player.server).all()) {
            maps.add(new MenuDataPacket.MapEntry(m.id, m.name, m.lootPoints.size(), m.centerX, m.centerZ, m.radius));
        }
        String status;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (br.isRunning() && br.getMap() != null) {
            status = "BR 進行中（地圖 #" + br.getMap().id + "）";
        } else if (com.provinggrounds.modes.arena.ArenaManager.current() != null) {
            status = com.provinggrounds.modes.arena.ArenaManager.current().title() + " 進行中";
        } else if (GameManager.get().isInLobby()) {
            status = "大廳";
        } else {
            status = GameManager.get().getMode() + " 進行中";
        }
        return new MenuDataPacket(s.kills, s.deaths, s.assists, s.wins, s.xp, level,
            PlayerDataManager.xpForNextLevel(level), player.hasPermissions(2),
            GameManager.get().isInLobby(), status, maps,
            com.provinggrounds.venue.VenueManager.visible().stream().map(com.provinggrounds.venue.Venue::name).toList());
    }
}
