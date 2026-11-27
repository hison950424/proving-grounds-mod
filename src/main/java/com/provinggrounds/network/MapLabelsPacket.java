package com.provinggrounds.network;

import com.provinggrounds.client.ClientMapData;
import com.provinggrounds.data.AltarLedger;
import com.provinggrounds.data.MapRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: MapLabelsPacket.java
 * 目的: 伺服器 → 客戶端。同步某張大逃殺地圖的「大區域名稱」與「重生祭壇位置」，
 *       讓大地圖與大廳預覽地圖都能顯示（祭壇不像遊戲中標記會過期）。
 */
public record MapLabelsPacket(int mapId, List<MapRegistry.RegionLabel> labels, List<BlockPos> altars) {

    public static MapLabelsPacket of(MinecraftServer server, MapRegistry.BRMap map) {
        List<BlockPos> altars = new ArrayList<>();
        for (BlockPos a : AltarLedger.get(server).entries()) {
            if (altars.size() >= 512) break;
            if (map.contains(a.getX() + 0.5, a.getZ() + 0.5)) altars.add(a.immutable());
        }
        return new MapLabelsPacket(map.id, List.copyOf(map.labels), List.copyOf(altars));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mapId);
        buf.writeVarInt(labels.size());
        for (MapRegistry.RegionLabel lb : labels) {
            buf.writeUtf(lb.name(), 32);
            buf.writeDouble(lb.x());
            buf.writeDouble(lb.z());
        }
        buf.writeVarInt(altars.size());
        for (BlockPos a : altars) buf.writeBlockPos(a);
    }

    public static MapLabelsPacket decode(FriendlyByteBuf buf) {
        int mapId = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 256);
        List<MapRegistry.RegionLabel> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new MapRegistry.RegionLabel(buf.readUtf(32), buf.readDouble(), buf.readDouble()));
        }
        int an = Math.min(buf.readVarInt(), 512);
        List<BlockPos> altars = new ArrayList<>(an);
        for (int i = 0; i < an; i++) altars.add(buf.readBlockPos());
        return new MapLabelsPacket(mapId, List.copyOf(list), List.copyOf(altars));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientMapData.setLabels(mapId, labels);
            ClientMapData.setAltars(mapId, altars);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void sendTo(ServerPlayer p, MapRegistry.BRMap map) {
        PGNetwork.sendTo(p, of(p.server, map));
    }

    public static void broadcast(MinecraftServer server, MapRegistry.BRMap map) {
        MapLabelsPacket pkt = of(server, map);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PGNetwork.sendTo(p, pkt);
        }
    }
}
