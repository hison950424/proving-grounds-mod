package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: DomGearPacket.java
 * 目的: 伺服器 → 客戶端。佔點特殊物品裝備的狀態（計畫書 8）：
 *   choice = 大廳選的三個（SpecialType 的順序）；inMatch = 佔點進行中；
 *   gear／cooldownTicks = 比賽中的三個裝備與剩餘冷卻（tick）；extras = 一次性的額外特殊物品 [種類, 剩餘次數]；
 *   selected = 現在放在特殊物品格的是哪一個（0～2 裝備、3 起額外的）。
 */
public record DomGearPacket(boolean inMatch, int[] choice, int[] gear, int[] cooldownTicks, List<int[]> extras, int selected) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inMatch);
        buf.writeVarIntArray(choice);
        buf.writeVarIntArray(gear);
        buf.writeVarIntArray(cooldownTicks);
        buf.writeVarInt(extras.size());
        for (int[] e : extras) { buf.writeVarInt(e[0]); buf.writeVarInt(e[1]); }
        buf.writeVarInt(selected);
    }

    public static DomGearPacket decode(FriendlyByteBuf buf) {
        boolean in = buf.readBoolean();
        int[] choice = buf.readVarIntArray(8), gear = buf.readVarIntArray(8), cd = buf.readVarIntArray(8);
        int n = Math.min(buf.readVarInt(), 64);
        List<int[]> extras = new ArrayList<>(n);
        for (int i = 0; i < n; i++) extras.add(new int[] { buf.readVarInt(), buf.readVarInt() });
        return new DomGearPacket(in, choice, gear, cd, extras, buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.client.ClientDomGear.set(this)));
        ctx.get().setPacketHandled(true);
    }
}
