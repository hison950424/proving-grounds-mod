package com.provinggrounds.network;

import com.provinggrounds.modes.arena.ArenaMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: ArenaStatePacket.java
 * 目的: 伺服器 → 客戶端。團隊死鬥／佔點／槍王之王的 HUD 與商店狀態（每 10 tick 一次）。
 *   kind 0 = 沒有對戰（客戶端清掉 HUD）。
 */
public record ArenaStatePacket(
    byte kind, String title, int seconds, String timerLabel, String status,
    List<ArenaMode.ScoreLine> scores,
    int money, int classOrdinal, boolean shop, boolean classes, boolean canShop, int team,
    int transferCooldown, int kills, int deaths, List<Mate> mates
) {

    /** 隊友（轉帳用）。 */
    public record Mate(UUID id, String name, int money) {}

    public static final ArenaStatePacket NONE = new ArenaStatePacket((byte) 0, "", -1, "", "", List.of(),
        0, 0, false, false, false, 0, 0, 0, 0, List.of());

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        if (kind == 0) return;
        buf.writeUtf(title, 64);
        buf.writeVarInt(seconds + 1);
        buf.writeUtf(timerLabel, 64);
        buf.writeUtf(status, 256);
        buf.writeVarInt(scores.size());
        for (ArenaMode.ScoreLine s : scores) {
            buf.writeInt(s.color());
            buf.writeUtf(s.label(), 32);
            buf.writeVarInt(s.value());
            buf.writeVarInt(s.target());
            buf.writeUtf(s.extra(), 64);
        }
        buf.writeVarInt(money);
        buf.writeVarInt(classOrdinal);
        buf.writeByte((shop ? 1 : 0) | (classes ? 2 : 0) | (canShop ? 4 : 0));
        buf.writeVarInt(team);
        buf.writeVarInt(transferCooldown);
        buf.writeVarInt(kills);
        buf.writeVarInt(deaths);
        buf.writeVarInt(mates.size());
        for (Mate m : mates) {
            buf.writeUUID(m.id());
            buf.writeUtf(m.name(), 32);
            buf.writeVarInt(m.money());
        }
    }

    public static ArenaStatePacket decode(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        if (kind == 0) return NONE;
        String title = buf.readUtf(64);
        int seconds = buf.readVarInt() - 1;
        String timerLabel = buf.readUtf(64);
        String status = buf.readUtf(256);
        int n = Math.min(buf.readVarInt(), 64);
        List<ArenaMode.ScoreLine> scores = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            scores.add(new ArenaMode.ScoreLine(buf.readInt(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));
        }
        int money = buf.readVarInt(), cls = buf.readVarInt();
        byte f = buf.readByte();
        int team = buf.readVarInt(), cd = buf.readVarInt(), kills = buf.readVarInt(), deaths = buf.readVarInt();
        int m = Math.min(buf.readVarInt(), 128);
        List<Mate> mates = new ArrayList<>(m);
        for (int i = 0; i < m; i++) mates.add(new Mate(buf.readUUID(), buf.readUtf(32), buf.readVarInt()));
        return new ArenaStatePacket(kind, title, seconds, timerLabel, status, scores, money, cls,
            (f & 1) != 0, (f & 2) != 0, (f & 4) != 0, team, cd, kills, deaths, mates);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.provinggrounds.client.ClientArenaState.set(this));
        ctx.get().setPacketHandled(true);
    }
}
