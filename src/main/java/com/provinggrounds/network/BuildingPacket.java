package com.provinggrounds.network;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.admin.build.BuildingCatalog;
import com.provinggrounds.item.AdminToolItem;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: BuildingPacket.java
 * 目的: 建築生成工具（建築款式選擇 UI ＋ 復原上一棟）雙向封包：
 *   action 0 (S→C): 開啟選擇 UI（附帶目前選中的建築 ID 與可復原筆數）
 *   action 1 (C→S): 玩家在 UI 選定了某棟建築 → 存進玩家選擇與手中的建築生成工具物品 NBT
 *   action 2 (C→S): 玩家在 UI 按下「復原上一棟」 → 還原上一棟方塊與物資點
 */
public record BuildingPacket(byte action, String buildingId, int undoCount) {

    public static final byte OPEN_UI = 0;
    public static final byte SELECT  = 1;
    public static final byte UNDO    = 2;

    public static BuildingPacket openUi(String selectedId, int undoCount) {
        return new BuildingPacket(OPEN_UI, selectedId == null ? "" : selectedId, undoCount);
    }

    public static BuildingPacket select(String buildingId) {
        return new BuildingPacket(SELECT, buildingId == null ? "" : buildingId, 0);
    }

    public static BuildingPacket undo() {
        return new BuildingPacket(UNDO, "", 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeUtf(buildingId, 64);
        buf.writeVarInt(undoCount);
    }

    public static BuildingPacket decode(FriendlyByteBuf buf) {
        return new BuildingPacket(buf.readByte(), buf.readUtf(64), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (action == OPEN_UI) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.provinggrounds.ui.BuildingPickerScreen.open(buildingId, undoCount));
                return;
            }
            ServerPlayer p = c.getSender();
            if (p == null || !AdminTools.allowed(p)) {
                if (p != null) p.sendSystemMessage(AdminTools.err("沒有權限（只有 OP 或創造模式能使用）。"));
                return;
            }
            if (action == SELECT) {
                var bp = BuildingCatalog.byId(buildingId);
                if (bp == null) return;
                BuildingCatalog.select(p.getUUID(), bp.id());
                writeToHeldTool(p.getMainHandItem(), bp.id());
                writeToHeldTool(p.getOffhandItem(), bp.id());
                p.inventoryMenu.broadcastChanges();
                p.displayClientMessage(Component.literal("已選取建築：【" + bp.style().displayName + "】" + bp.name()
                    + "（" + bp.sizeText() + "，" + bp.lootCount() + " 個物資點）").withStyle(ChatFormatting.AQUA), true);
            } else if (action == UNDO) {
                p.sendSystemMessage(BuildingCatalog.undo(p));
            }
        });
        c.setPacketHandled(true);
    }

    private static void writeToHeldTool(ItemStack stack, String id) {
        if (stack.is(PGBlocks.ADMIN_TOOLS.get(AdminToolItem.Tool.BUILDING_PLACER).get())) {
            stack.getOrCreateTag().putString(BuildingCatalog.TOOL_NBT, id);
        }
    }
}
