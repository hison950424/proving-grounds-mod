package com.provinggrounds.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: SpecialFxPacket.java
 * 目的: 伺服器 → 客戶端。特殊物品的客戶端效果：
 *   COUGH       咳嗽（視角先上揚再往下）；
 *   DISORIENT   失去方向感：a = 方向（0 前、1 後、2 左、3 右），b = 持續 tick；
 *   RADAR       雷達脈衝：在 (x, y, z) 畫一圈往外擴散的環，a = 半徑；
 *   SELF_REVIVE 自救讀條：a = 剩下 tick、b = 總 tick（a = 0 代表結束）。
 */
public record SpecialFxPacket(byte kind, int a, int b, double x, double y, double z) {

    public static final byte COUGH = 0, DISORIENT = 1, RADAR = 2, SELF_REVIVE = 3, PEARL = 4, FORCE_LOOK_UP = 5; // PEARL：a = 1 傳送珠飛行中、0 結束；FORCE_LOOK_UP：強制看向正上方

    public static SpecialFxPacket of(byte kind, int a, int b) { return new SpecialFxPacket(kind, a, b, 0, 0, 0); }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static SpecialFxPacket decode(FriendlyByteBuf buf) {
        return new SpecialFxPacket(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.provinggrounds.special.client.ClientSpecial.applyFx(this)));
        ctx.get().setPacketHandled(true);
    }
}
