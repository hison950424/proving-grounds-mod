package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: DomGearPacket.java
 * 目的: 伺服器 → 客戶端。佔點整場特殊物品的狀態（計畫書 8）：
 *   choice = 大廳選的那一種（SpecialType 的順序）；inMatch = 佔點進行中、而且有發特殊物品；
 *   gear = 比賽中的那一種（-1 = 沒有）；used = 這回合用過了。
 */
public record DomGearPacket(boolean inMatch, int choice, int gear, boolean used) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inMatch);
        buf.writeVarInt(choice);
        buf.writeVarInt(gear);
        buf.writeBoolean(used);
    }

    public static DomGearPacket decode(FriendlyByteBuf buf) {
        return new DomGearPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.client.ClientDomGear.set(this)));
        ctx.get().setPacketHandled(true);
    }
}
