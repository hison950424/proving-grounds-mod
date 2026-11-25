package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: PingSyncPacket.java
 * 目的: 標點（使用者回饋）。伺服器 → 客戶端：自己或隊友的新標點（同一個人的新標點會取代舊的）。畫面由 ui/ClientPing 處理。
 */
public record PingSyncPacket(UUID owner, String name, double x, double y, double z) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(owner);
        buf.writeUtf(name, 32);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static PingSyncPacket decode(FriendlyByteBuf buf) {
        return new PingSyncPacket(buf.readUUID(), buf.readUtf(32), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.provinggrounds.ui.ClientPing.receive(this)));
        ctx.get().setPacketHandled(true);
    }
}
