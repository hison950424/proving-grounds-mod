package com.provinggrounds.network;

import com.provinggrounds.client.ClientBRState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: KillFeedPacket.java
 * 目的: 擊殺資訊（HUD 右上角、存活人數下方，淡出）。killer 為空字串代表非玩家致死（縮圈、流血等）。
 *       knock = true 表示「擊倒」（倒地），false 表示「淘汰」。顏色為隊伍顏色的 RGB 值。
 *       聊天室不再顯示擊殺／擊倒訊息，全部改在這裡。
 *       flags（計畫書 7 F）：跟 KillNoticePacket 一樣的位元旗標（1 = 爆頭、2 = 穿牆）。
 */
public record KillFeedPacket(String killer, int killerColor, String victim, int victimColor, boolean knock, int flags) {

    public KillFeedPacket(String killer, int killerColor, String victim, int victimColor, boolean knock) {
        this(killer, killerColor, victim, victimColor, knock, 0);
    }

    public boolean headshot() { return (flags & KillNoticePacket.HEADSHOT) != 0; }

    public boolean wallbang() { return (flags & KillNoticePacket.WALLBANG) != 0; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(killer, 64);
        buf.writeInt(killerColor);
        buf.writeUtf(victim, 64);
        buf.writeInt(victimColor);
        buf.writeBoolean(knock);
        buf.writeByte(flags);
    }

    public static KillFeedPacket decode(FriendlyByteBuf buf) {
        return new KillFeedPacket(buf.readUtf(64), buf.readInt(), buf.readUtf(64), buf.readInt(), buf.readBoolean(), buf.readByte() & 0xFF);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientBRState.addKill(this));
        ctx.get().setPacketHandled(true);
    }
}
