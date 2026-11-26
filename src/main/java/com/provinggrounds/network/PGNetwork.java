package com.provinggrounds.network;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 檔案: PGNetwork.java
 * 目的: 伺服器 ↔ 客戶端封包通道。客戶端必須安裝同版本 Mod 才能連線。
 */
public final class PGNetwork {

    /** 封包格式改變時要加 1，舊版客戶端連線時會直接顯示版本不符（7 = 跳傘改版）。 */
    private static final String VERSION = "29"; // 29 = 建築生成工具封包, 28 = 計畫書 10, 27 = 計畫書 9

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(ProvingGroundsMod.MOD_ID, "main"),
        () -> VERSION, VERSION::equals, VERSION::equals);

    private PGNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, BRStatePacket.class, BRStatePacket::encode, BRStatePacket::decode,
            BRStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, KillFeedPacket.class, KillFeedPacket::encode, KillFeedPacket::decode,
            KillFeedPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MenuDataPacket.class, MenuDataPacket::encode, MenuDataPacket::decode,
            MenuDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MenuRequestPacket.class, MenuRequestPacket::encode, MenuRequestPacket::decode,
            MenuRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, LobbyStatePacket.class, LobbyStatePacket::encode, LobbyStatePacket::decode,
            LobbyStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, LobbyActionPacket.class, LobbyActionPacket::encode, LobbyActionPacket::decode,
            LobbyActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapImagePacket.class, MapImagePacket::encode, MapImagePacket::decode,
            MapImagePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapMarkersPacket.class, MapMarkersPacket::encode, MapMarkersPacket::decode,
            MapMarkersPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ProneSyncPacket.class, ProneSyncPacket::encode, ProneSyncPacket::decode,
            ProneSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ProneTogglePacket.class, ProneTogglePacket::encode, ProneTogglePacket::decode,
            ProneTogglePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SkydiveKeyPacket.class, SkydiveKeyPacket::encode, SkydiveKeyPacket::decode,
            SkydiveKeyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, WheelSelectPacket.class, WheelSelectPacket::encode, WheelSelectPacket::decode,
            WheelSelectPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MatchResultPacket.class, MatchResultPacket::encode, MatchResultPacket::decode,
            MatchResultPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StatsPersonalPacket.class, StatsPersonalPacket::encode, StatsPersonalPacket::decode,
            StatsPersonalPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StatsLeaderboardPacket.class, StatsLeaderboardPacket::encode, StatsLeaderboardPacket::decode,
            StatsLeaderboardPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SpawnPointsPacket.class, SpawnPointsPacket::encode, SpawnPointsPacket::decode,
            SpawnPointsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 雙向：伺服器叫客戶端開輸入畫面、客戶端送回文字
        CHANNEL.registerMessage(id++, FloatTextPacket.class, FloatTextPacket::encode, FloatTextPacket::decode,
            FloatTextPacket::handle, Optional.empty());
        CHANNEL.registerMessage(id++, BackpackSyncPacket.class, BackpackSyncPacket::encode, BackpackSyncPacket::decode,
            BackpackSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SpawnPointFlashPacket.class, SpawnPointFlashPacket::encode, SpawnPointFlashPacket::decode,
            SpawnPointFlashPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 雙向：伺服器送地圖清單、客戶端送回選擇
        CHANNEL.registerMessage(id++, MapPickPacket.class, MapPickPacket::encode, MapPickPacket::decode,
            MapPickPacket::handle, Optional.empty());
        // 特殊物品（計畫書 4）
        CHANNEL.registerMessage(id++, SpecialSlotSyncPacket.class, SpecialSlotSyncPacket::encode, SpecialSlotSyncPacket::decode,
            SpecialSlotSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SpecialUsePacket.class, SpecialUsePacket::encode, SpecialUsePacket::decode,
            SpecialUsePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, GlowSyncPacket.class, GlowSyncPacket::encode, GlowSyncPacket::decode,
            GlowSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, QuietWalkPacket.class, QuietWalkPacket::encode, QuietWalkPacket::decode,
            QuietWalkPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SpecialFxPacket.class, SpecialFxPacket::encode, SpecialFxPacket::decode,
            SpecialFxPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ArenaStatePacket.class, ArenaStatePacket::encode, ArenaStatePacket::decode,
            ArenaStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ArenaActionPacket.class, ArenaActionPacket::encode, ArenaActionPacket::decode,
            ArenaActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, KillNoticePacket.class, KillNoticePacket::encode, KillNoticePacket::decode,
            KillNoticePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, InteractKeyPacket.class, InteractKeyPacket::encode, InteractKeyPacket::decode,

            InteractKeyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ChuteSyncPacket.class, ChuteSyncPacket::encode, ChuteSyncPacket::decode,
            ChuteSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)); // 開傘中的玩家（計畫書 6）
        CHANNEL.registerMessage(id++, BRBoardPacket.class, BRBoardPacket::encode, BRBoardPacket::decode,
            BRBoardPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)); // 大逃殺戰績面板（計畫書 6）
        CHANNEL.registerMessage(id++, MeleeGlassPacket.class, MeleeGlassPacket::encode, MeleeGlassPacket::decode,
            MeleeGlassPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER)); // 近戰打破玻璃（計畫書 6）
        CHANNEL.registerMessage(id++, ElevatorPacket.class, ElevatorPacket::encode, ElevatorPacket::decode,
            ElevatorPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER)); // 電梯按鍵（計畫書 7 A5）
        CHANNEL.registerMessage(id++, PingPacket.class, PingPacket::encode, PingPacket::decode,
            PingPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER)); // 標點
        CHANNEL.registerMessage(id++, PingSyncPacket.class, PingSyncPacket::encode, PingSyncPacket::decode,
            PingSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)); // 標點同步給隊友
        CHANNEL.registerMessage(id++, DomGearPacket.class, DomGearPacket::encode, DomGearPacket::decode,
            DomGearPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)); // 佔點特殊物品裝備（計畫書 8）
        CHANNEL.registerMessage(id++, DomGearActionPacket.class, DomGearActionPacket::encode, DomGearActionPacket::decode,
            DomGearActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Plan9SyncPacket.class, Plan9SyncPacket::encode, Plan9SyncPacket::decode,
            Plan9SyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)); // 計畫書 9 特殊道具狀態同步
        CHANNEL.registerMessage(id++, IceSkateCrashPacket.class, IceSkateCrashPacket::encode, IceSkateCrashPacket::decode,
            IceSkateCrashPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER)); // 計畫書 10：溜冰鞋撞牆速度傷害
        CHANNEL.registerMessage(id++, BuildingPacket.class, BuildingPacket::encode, BuildingPacket::decode,
            BuildingPacket::handle, Optional.empty()); // 管理員大逃殺建築生成工具（雙向：開選單／選定建築／復原）

    }

    /** 客戶端 → 伺服器。 */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
