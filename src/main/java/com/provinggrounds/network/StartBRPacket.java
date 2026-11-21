package com.provinggrounds.network;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.CirclePreset;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: StartBRPacket.java
 * 目的: 客戶端 → 伺服器。主選單按下「開始」時送出，等同 /pg br start。
 *       伺服器端會再檢查一次 OP 權限（等級 2），客戶端無法繞過。
 */
public record StartBRPacket(int mapId, int presetOrdinal, int variantOrdinal) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mapId);
        buf.writeVarInt(presetOrdinal);
        buf.writeVarInt(variantOrdinal);
    }

    public static StartBRPacket decode(FriendlyByteBuf buf) {
        return new StartBRPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("[PG] 只有管理員可以開始遊戲。").withStyle(ChatFormatting.RED));
                return;
            }
            CirclePreset[] presets = CirclePreset.values();
            BattleRoyaleMode.Variant[] variants = BattleRoyaleMode.Variant.values();
            if (presetOrdinal < 0 || presetOrdinal >= presets.length || variantOrdinal < 0 || variantOrdinal >= variants.length) {
                return; // 不合法的值直接忽略
            }
            String error = BattleRoyaleMode.get().start(player.server, mapId, presets[presetOrdinal], variants[variantOrdinal]);
            if (error != null) {
                player.sendSystemMessage(Component.literal("[PG] " + error).withStyle(ChatFormatting.RED));
            } else {
                ProvingGroundsMod.LOGGER.info("BR started from menu by {}", player.getGameProfile().getName());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
