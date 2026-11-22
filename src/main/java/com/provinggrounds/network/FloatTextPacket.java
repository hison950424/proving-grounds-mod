package com.provinggrounds.network;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.admin.FloatText;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: FloatTextPacket.java
 * 目的: 浮空文字（計畫書 B6）的兩個方向：
 *   伺服器 → 客戶端（text = null）：開啟「輸入文字」畫面；
 *   客戶端 → 伺服器（text = 輸入內容）：建立文字。伺服器重新檢查權限與長度，不信任客戶端。
 */
public record FloatTextPacket(String text, boolean open) {

    private static final int MAX_BYTES = FloatText.MAX_LENGTH * 4 + 16;

    public static FloatTextPacket openScreen() { return new FloatTextPacket("", true); }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeUtf(text, MAX_BYTES);
    }

    public static FloatTextPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        return new FloatTextPacket(buf.readUtf(MAX_BYTES), open);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (open) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.provinggrounds.ui.FloatTextScreen::open);
                return;
            }
            ServerPlayer p = c.getSender();
            if (p == null) return;
            if (!AdminTools.allowed(p)) {
                p.sendSystemMessage(AdminTools.err("沒有權限。"));
                return;
            }
            p.sendSystemMessage(FloatText.create(p, text));
        });
        c.setPacketHandled(true);
    }
}
