package com.provinggrounds.network;

import com.provinggrounds.special.DomGear;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: DomGearActionPacket.java
 * 目的: 客戶端 → 伺服器。佔點特殊物品裝備的操作（計畫書 8），規則都在伺服器（DomGear）檢查：
 *   SELECT（輪盤選好，a = 第幾個）、SET（換裝備，a = 第幾格、b = SpecialType 的順序）、BUY（商店買一次性的，a = 種類）。
 */
public record DomGearActionPacket(Action action, int a, int b) {

    public enum Action { SELECT, SET, BUY }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
    }

    public static DomGearActionPacket decode(FriendlyByteBuf buf) {
        return new DomGearActionPacket(buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            switch (action) {
                case SELECT -> DomGear.select(p, a);
                case SET -> DomGear.setGear(p, a, b);
                case BUY -> DomGear.buy(p, a);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
