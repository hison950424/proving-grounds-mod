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

    /** 封包格式改變時要加 1，舊版客戶端連線時會直接顯示版本不符（6 = 趴下）。 */
    private static final String VERSION = "6";

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
