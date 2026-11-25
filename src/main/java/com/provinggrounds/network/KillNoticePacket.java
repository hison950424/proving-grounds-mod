package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: KillNoticePacket.java
 * 目的: 伺服器 → 客戶端。擊殺提示（計畫書 5 的 E3）。
 *   feed = true：加進右上角擊殺紀錄（團隊死鬥／佔點／槍王之王、迷你死鬥；大逃殺有自己的擊殺資訊）。
 *   mine = true：收到的人就是擊殺者 → 準星下方彈出小圖示＋擊殺音效（連殺數越高音越高，爆頭另一組音）。
 *   flags（計畫書 7 F，1 byte 位元旗標，好擴充）：HEADSHOT = 爆頭、WALLBANG = 穿牆。
 */
public record KillNoticePacket(boolean feed, boolean mine, String killer, int killerColor, String victim, int victimColor,
                               ItemStack weapon, int flags, int streak) {

    public static final int HEADSHOT = 1, WALLBANG = 2;

    public static int flags(boolean headshot, boolean wallbang) { return (headshot ? HEADSHOT : 0) | (wallbang ? WALLBANG : 0); }

    public boolean headshot() { return (flags & HEADSHOT) != 0; }

    public boolean wallbang() { return (flags & WALLBANG) != 0; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(feed);
        buf.writeBoolean(mine);
        buf.writeUtf(killer, 64);
        buf.writeInt(killerColor);
        buf.writeUtf(victim, 64);
        buf.writeInt(victimColor);
        buf.writeItem(weapon);
        buf.writeByte(flags);
        buf.writeVarInt(streak);
    }

    public static KillNoticePacket decode(FriendlyByteBuf buf) {
        return new KillNoticePacket(buf.readBoolean(), buf.readBoolean(), buf.readUtf(64), buf.readInt(), buf.readUtf(64), buf.readInt(),
            buf.readItem(), buf.readByte() & 0xFF, buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.provinggrounds.ui.KillHud.receive(this));
        ctx.get().setPacketHandled(true);
    }
}
