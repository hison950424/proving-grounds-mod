package com.provinggrounds.network;

import com.provinggrounds.events.InteractEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: InteractKeyPacket.java
 * 目的: 客戶端 → 伺服器。玩家按了互動鍵（預設 F；計畫書 5 的 B1、B2）。
 *   每按一次送兩個：
 *     MARK：在原版互動封包之前送，讓伺服器知道接下來這次右鍵互動是 F 鍵造成的（拿槍時才不會被擋）；
 *     DONE：原版互動之後送，附上「有沒有真的和方塊／實體互動」與準星指著的實體，
 *           沒有互動時伺服器再找附近的倒地隊友或祭壇開始讀條。
 */
public record InteractKeyPacket(boolean done, boolean interacted, int entityId) {

    public static InteractKeyPacket mark() { return new InteractKeyPacket(false, false, -1); }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(done);
        buf.writeBoolean(interacted);
        buf.writeVarInt(entityId + 1);
    }

    public static InteractKeyPacket decode(FriendlyByteBuf buf) {
        return new InteractKeyPacket(buf.readBoolean(), buf.readBoolean(), buf.readVarInt() - 1);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (com.provinggrounds.special.ActionLock.brainwashed(p)) return; // 洗腦中不能互動（計畫書 6 E1）
            if (!done) InteractEvents.markKey(p);
            else InteractEvents.onKeyDone(p, interacted, entityId);
        });
        ctx.get().setPacketHandled(true);
    }
}
