package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: GlowSyncPacket.java
 * 目的: 伺服器 → 客戶端。這個玩家「現在看得到誰在發光」的完整清單（發光引擎 GlowManager 每 0.5 秒送一次，清單變空時也送一次）。
 *   每筆：實體編號（看得到時畫發光外框）、座標（強力發光時畫在地圖上）、是否上地圖、顏色。
 *   只送給指定的觀眾，所以「單向發光」別人完全看不到。
 */
public record GlowSyncPacket(List<Entry> entries) {

    public record Entry(int entityId, float x, float z, boolean map, int color) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeVarInt(e.entityId());
            buf.writeFloat(e.x());
            buf.writeFloat(e.z());
            buf.writeBoolean(e.map());
            buf.writeInt(e.color());
        }
    }

    public static GlowSyncPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new Entry(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readInt()));
        return new GlowSyncPacket(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.special.client.ClientSpecial.applyGlow(entries)));
        ctx.get().setPacketHandled(true);
    }
}
