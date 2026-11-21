package com.provinggrounds.network;

import com.provinggrounds.client.ClientMapData;
import com.provinggrounds.data.MapImageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 檔案: MapImagePacket.java
 * 目的: 伺服器 → 客戶端。地圖俯視圖（壓縮過的像素），太大所以切成多段傳送，客戶端收齊後組回來。
 *       開局、遊戲中登入時送出；客戶端已經有同一版本就直接略過。
 */
public record MapImagePacket(int mapId, long version, int minX, int minZ, int size, int scale,
                             int part, int totalParts, byte[] bytes) {

    private static final int PART_SIZE = 30000;

    /** 把整張圖切段送給一位玩家。 */
    public static void sendTo(ServerPlayer player, MapImageStore.MapImage img) {
        byte[] data = img.data();
        int total = Math.max(1, (data.length + PART_SIZE - 1) / PART_SIZE);
        for (int i = 0; i < total; i++) {
            byte[] slice = Arrays.copyOfRange(data, i * PART_SIZE, Math.min(data.length, (i + 1) * PART_SIZE));
            PGNetwork.sendTo(player, new MapImagePacket(img.mapId(), img.version(), img.minX(), img.minZ(),
                img.size(), img.scale(), i, total, slice));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mapId);
        buf.writeLong(version);
        buf.writeInt(minX);
        buf.writeInt(minZ);
        buf.writeVarInt(size);
        buf.writeVarInt(scale);
        buf.writeVarInt(part);
        buf.writeVarInt(totalParts);
        buf.writeByteArray(bytes);
    }

    public static MapImagePacket decode(FriendlyByteBuf buf) {
        return new MapImagePacket(buf.readVarInt(), buf.readLong(), buf.readInt(), buf.readInt(),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PART_SIZE + 16));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientMapData.receivePart(this));
        ctx.get().setPacketHandled(true);
    }
}
