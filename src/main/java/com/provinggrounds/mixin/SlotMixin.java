package com.provinggrounds.mixin;

import com.provinggrounds.modes.br.Backpack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: SlotMixin.java
 * 目的: 主背包鎖定格不能放、不能拿（計畫書 B3）。所有畫面（物品欄、箱子等）裡代表玩家主背包的格子都經過這裡，
 *       伺服器與客戶端都檢查（伺服器決定結果，客戶端只是讓畫面一致）。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（mayPlace = m_5857_，mayPickup = m_8010_），不需要 refmap。
 *   注意：有些格子類別覆寫了 mayPlace 而不呼叫原版（例如防具欄），那些都不是主背包格，不受影響。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Inject(method = { "mayPlace", "m_5857_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$lockedPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (self.container instanceof Inventory && self.getContainerSlot() == 40) { cir.setReturnValue(false); return; } // 副手格：玩家不能放（計畫書 4 的 A6）
        if (provinggrounds$locked()) cir.setReturnValue(false);
    }

    @Inject(method = { "mayPickup", "m_8010_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$lockedPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (provinggrounds$locked()) cir.setReturnValue(false);
    }

    private boolean provinggrounds$locked() {
        Slot self = (Slot) (Object) this; // 一般的成員存取，打包時會轉成正式環境名稱
        return self.container instanceof Inventory inv && Backpack.locked(inv.player, self.getContainerSlot());
    }
}
