package com.provinggrounds.mixin;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: CreativeSlotMixin.java
 * 目的: 創造模式物品欄改背包欄（第 46 格）、特殊物品欄（第 47 格）時，伺服器也要跟著改。
 *   原版伺服器只接受第 1～45 格的創造模式修改，超過的直接忽略，畫面上看起來放進去了、其實沒有。
 *   這裡在伺服器執行緒上處理這兩格（照各自的規則：背包欄只收背包、特殊物品欄只收特殊物品），其他格子照原版。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（handleSetCreativeModeSlot = m_5964_），不需要 refmap。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotMixin {

    @Inject(method = { "handleSetCreativeModeSlot", "m_5964_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$extraSlots(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        int n = packet.getSlotNum();
        if (n != 46 && n != 47) return;
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player; // 一般的成員存取，打包時會轉成正式環境名稱
        if (!player.server.isSameThread()) return; // 原版會把封包排到伺服器執行緒再呼叫一次
        ci.cancel();
        if (!player.isCreative() || n >= player.inventoryMenu.slots.size()) return;
        Slot slot = player.inventoryMenu.getSlot(n);
        ItemStack stack = packet.getItem();
        if (stack.isEmpty() || slot.mayPlace(stack)) slot.set(stack.copy());
        player.inventoryMenu.broadcastChanges();
    }
}
