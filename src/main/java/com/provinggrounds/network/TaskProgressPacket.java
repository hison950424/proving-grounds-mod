package com.provinggrounds.network;

import com.provinggrounds.client.ClientTaskProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: TaskProgressPacket.java
 * 目的: 伺服器 → 客戶端。同步地圖生成／修復光照／繪製俯視圖／掃描等耗時工作的目前步驟與百分比進度條。
 */
public record TaskProgressPacket(boolean active, String title, String stage, float progress, boolean done) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeUtf(title == null ? "" : title, 64);
        buf.writeUtf(stage == null ? "" : stage, 128);
        buf.writeFloat(progress);
        buf.writeBoolean(done);
    }

    public static TaskProgressPacket decode(FriendlyByteBuf buf) {
        return new TaskProgressPacket(
            buf.readBoolean(),
            buf.readUtf(64),
            buf.readUtf(128),
            buf.readFloat(),
            buf.readBoolean()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTaskProgress.update(this));
        ctx.get().setPacketHandled(true);
    }
}
