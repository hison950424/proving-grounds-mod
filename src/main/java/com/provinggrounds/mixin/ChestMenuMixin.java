package com.provinggrounds.mixin;

import com.provinggrounds.block.ChestExtraSlots;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: ChestMenuMixin.java
 * 目的: 計畫書 7 A4：箱子畫面（所有 ChestMenu：箱子、木桶、物資箱、終界箱…）最後加上防具、副手、背包欄、特殊物品欄，
 *   讓開箱子時也能穿防具、放特殊物品（ChestExtraSlots）。伺服器與客戶端都加，格子內容由原版同步。
 *   只注入「真正建立格子」的那個建構子（其他建構子都會呼叫它，避免加兩次）。不需要 refmap。
 */
@Mixin(ChestMenu.class)
public abstract class ChestMenuMixin extends AbstractContainerMenu {

    protected ChestMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;I)V",
        at = @At("RETURN"), remap = false)
    private void provinggrounds$addExtraSlots(MenuType<?> type, int id, Inventory inv, Container container, int rows, CallbackInfo ci) {
        for (Slot s : ChestExtraSlots.create(inv)) this.addSlot(s);
    }
}
