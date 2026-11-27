package com.provinggrounds.mixin;

import com.provinggrounds.modes.br.HotbarRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: PlayerInventoryMixin.java
 * 目的: 攔截玩家背包的 Inventory.add(slot, stack)（包含 TACZ 按 Z 卸下配件、指令與其他模組發放物品）：
 *   當 HotbarRules 對該玩家生效且未指定特定格子（slot == -1）時，改走 HotbarRules.insert(sp, stack)，
 *   絕不把一般物品／配件／子彈塞進快捷欄第 1～5 專用格或已鎖定的背包格；若放不下則回傳 false（讓 TACZ 取消卸下或改丟地上）。
 */
@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow @Final public Player player;

    @Inject(method = { "add(ILnet/minecraft/world/item/ItemStack;)Z", "m_36040_" },
            at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$respectHotbarRulesOnAdd(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != -1 || stack.isEmpty()) return;
        if (!(player instanceof ServerPlayer sp) || !HotbarRules.active(sp)) return;

        int before = stack.getCount();
        HotbarRules.Result res = HotbarRules.insert(sp, stack);
        if (res.moved() > 0) {
            sp.inventoryMenu.broadcastChanges();
        }
        if (stack.isEmpty()) {
            cir.setReturnValue(true);
        } else {
            if (before == stack.getCount() && res.message() != null) {
                HotbarRules.message(sp, res.message());
            }
            cir.setReturnValue(false);
        }
    }
}
