package com.provinggrounds.mixin;

import com.provinggrounds.modes.br.Backpack;
import com.provinggrounds.modes.br.BackpackSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: InventoryMenuMixin.java
 * 目的: 在玩家物品欄（InventoryMenu）最後加一格「背包」欄（計畫書 B3）。伺服器與客戶端都加，格子內容由原版同步。
 *   加在最後（第 46 格），原版與其他模組用到的格子編號都不變。
 *   為什麼用 Mixin：Forge 沒有替原版物品欄加格子的事件；計畫書要求不依賴 Curios 等外部模組。
 *   addSlot 的呼叫在打包時會被轉成正式環境名稱（reobf），注入點只寫建構子，不需要 refmap。
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    protected InventoryMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void provinggrounds$addBackpackSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        this.addSlot(new BackpackSlot(Backpack.container(owner)));
        this.addSlot(new com.provinggrounds.special.SpecialSlot.MenuSlot(com.provinggrounds.special.SpecialSlot.container(owner))); // 第 47 格：特殊物品欄（計畫書 4 的 B1）
    }
}
