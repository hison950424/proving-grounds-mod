package com.provinggrounds.network;

import com.provinggrounds.client.ClientProneData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: ProneSyncPacket.java
 * 目的: 伺服器 → 客戶端。目前正在趴著的玩家名單（有變動時廣播、登入時送一次），
 *       客戶端據此替這些玩家套用趴下姿勢並放慢趴下／站起的動畫。
 */
public record ProneSyncPacket(List<UUID> prone) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(prone.size());
        for (UUID id : prone) buf.writeUUID(id);
    }

    public static ProneSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 1000);
        List<UUID> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUUID());
        return new ProneSyncPacket(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientProneData.set(prone));
        ctx.get().setPacketHandled(true);
    }
}
