package com.provinggrounds.network;

import com.provinggrounds.client.ClientBRState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 檔案: BRStatePacket.java
 * 目的: 伺服器每 5 tick 送給每位玩家的 BR 狀態（HUD、縮圈牆、跳躍修正、倒地姿勢用）。
 *       phase / selfState / progressKind / timerKind 的數值對照見 {@link ClientBRState}。
 */
public record BRStatePacket(
    byte phase,           // 0 無, 1 準備, 2 飛機, 3 進行中, 4 結算
    int seconds,          // 準備倒數 / 距離下次縮圈開始或結束的秒數
    boolean shrinking,
    byte stage,           // 目前階段（1 起算），0 = 尚未開始
    byte totalStages,
    float cx, float cz, float radius,
    float nextCx, float nextCz, float nextRadius, // nextRadius < 0 表示沒有下一圈
    short alivePlayers,
    short aliveTeams,
    byte selfState,       // 0 非參賽, 1 飛機上, 2 自由落體, 3 開傘, 4 地面存活, 5 已淘汰, 6 倒地
    float jumpHeight,     // >0 時客戶端套用 BR 跳躍修正
    byte progressKind,    // 讀條種類：0 無, 1 正在扶隊友, 2 隊友正在扶你, 3 啟動祭壇
    float progress,       // 讀條進度 0~1
    byte timerKind,       // 倒數種類：0 無, 1 流血, 2 復活牌在地上, 3 復活牌已被撿起, 4 已確定死亡
    short timerSeconds,
    byte cardsHeld,       // 身上持有的復活牌數
    List<UUID> crawling,  // 正在趴地的倒地玩家（客戶端強制趴下姿勢用）
    List<UUID> diving     // 自由落體中的玩家（客戶端畫頭朝下姿勢用）
) {

    public static BRStatePacket none() {
        return new BRStatePacket((byte) 0, 0, false, (byte) 0, (byte) 0, 0, 0, -1, 0, 0, -1,
            (short) 0, (short) 0, (byte) 0, 0f, (byte) 0, 0f, (byte) 0, (short) 0, (byte) 0, List.of(), List.of());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(phase);
        buf.writeVarInt(seconds);
        buf.writeBoolean(shrinking);
        buf.writeByte(stage);
        buf.writeByte(totalStages);
        buf.writeFloat(cx); buf.writeFloat(cz); buf.writeFloat(radius);
        buf.writeFloat(nextCx); buf.writeFloat(nextCz); buf.writeFloat(nextRadius);
        buf.writeShort(alivePlayers);
        buf.writeShort(aliveTeams);
        buf.writeByte(selfState);
        buf.writeFloat(jumpHeight);
        buf.writeByte(progressKind);
        buf.writeFloat(progress);
        buf.writeByte(timerKind);
        buf.writeShort(timerSeconds);
        buf.writeByte(cardsHeld);
        buf.writeVarInt(crawling.size());
        for (UUID id : crawling) buf.writeUUID(id);
        buf.writeVarInt(diving.size());
        for (UUID id : diving) buf.writeUUID(id);
    }

    public static BRStatePacket decode(FriendlyByteBuf buf) {
        byte phase = buf.readByte();
        int seconds = buf.readVarInt();
        boolean shrinking = buf.readBoolean();
        byte stage = buf.readByte(), total = buf.readByte();
        float cx = buf.readFloat(), cz = buf.readFloat(), r = buf.readFloat();
        float nx = buf.readFloat(), nz = buf.readFloat(), nr = buf.readFloat();
        short alive = buf.readShort(), teams = buf.readShort();
        byte self = buf.readByte();
        float jump = buf.readFloat();
        byte progressKind = buf.readByte();
        float progress = buf.readFloat();
        byte timerKind = buf.readByte();
        short timer = buf.readShort();
        byte cards = buf.readByte();
        int n = Math.min(buf.readVarInt(), 256);
        List<UUID> crawling = new ArrayList<>(n);
        for (int i = 0; i < n; i++) crawling.add(buf.readUUID());
        int dn = Math.min(buf.readVarInt(), 256);
        List<UUID> diving = new ArrayList<>(dn);
        for (int i = 0; i < dn; i++) diving.add(buf.readUUID());
        return new BRStatePacket(phase, seconds, shrinking, stage, total, cx, cz, r, nx, nz, nr,
            alive, teams, self, jump, progressKind, progress, timerKind, timer, cards, crawling, diving);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientBRState.apply(this));
        ctx.get().setPacketHandled(true);
    }
}
