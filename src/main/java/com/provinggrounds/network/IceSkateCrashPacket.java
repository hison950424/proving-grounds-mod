package com.provinggrounds.network;

import com.provinggrounds.special.Plan9Specials;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: IceSkateCrashPacket.java
 * 目的: 客戶端 → 伺服器。溜冰鞋高速滑行撞到方塊牆壁時回報撞擊速度，由伺服器依速度比例給予傷害。
 */
public record IceSkateCrashPacket(float speed) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(speed);
    }

    public static IceSkateCrashPacket decode(FriendlyByteBuf buf) {
        return new IceSkateCrashPacket(buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Plan9Specials.onIceSkateCrash(p, speed);
        });
        ctx.get().setPacketHandled(true);
    }
}
