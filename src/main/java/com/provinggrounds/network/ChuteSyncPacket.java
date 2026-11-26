package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 檔案: ChuteSyncPacket.java
 * 目的: 伺服器 → 客戶端：現在「降落傘打開中」的玩家（實體編號）。計畫書 6 D4：跳傘不再用降落傘物品，
 *   所以客戶端改看這份清單畫傘衣、舉手（ParachuteRenderer、HumanoidArmsMixin）。有變化才送。
 */
public record ChuteSyncPacket(int[] ids) {

    /** 客戶端：打開降落傘中的實體編號。 */
    private static volatile Set<Integer> open = Set.of();

    /** 大逃殺與非大逃殺重新跳傘皆可顯示傘衣。 */
    public static boolean isOpen(int entityId) { return open.contains(entityId); }

    public static void clear() { open = Set.of(); }

    public void encode(FriendlyByteBuf buf) { buf.writeVarIntArray(ids); }

    public static ChuteSyncPacket decode(FriendlyByteBuf buf) { return new ChuteSyncPacket(buf.readVarIntArray(1024)); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Set<Integer> s = new HashSet<>();
            for (int id : ids) s.add(id);
            open = Set.copyOf(s);
        });
        ctx.get().setPacketHandled(true);
    }
}
