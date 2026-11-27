package com.provinggrounds.mixin;

import com.provinggrounds.modes.br.HotbarRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 檔案: ContainerMenuMixin.java
 * 目的: 快捷欄規則的點擊檢查（計畫書 B2）。所有容器畫面（含自己的物品欄）的點擊都經過 AbstractContainerMenu.clicked，
 *       包含滑鼠點擊、Shift 快速移動、拖曳分堆、數字鍵交換、F 鍵換到副手、雙擊收集。
 *       只在伺服器端、規則生效的玩家身上作用；客戶端畫面上先看到的結果會被伺服器更正回來。
 *   為什麼用 Mixin：Forge 沒有「容器點擊」的事件可以取消。
 *   方法名稱同時寫開發環境名稱（clicked）與 SRG 名稱（m_150399_），不需要 refmap。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {

    private static final ThreadLocal<HotbarRules.Snapshot> provinggrounds$snapshot = new ThreadLocal<>();

    @Inject(method = { "clicked", "m_150399_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$beforeClick(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        provinggrounds$snapshot.remove();
        // 副手不能主動放東西（計畫書 4 的 A6）：物品欄裡把游標指著的格子和副手對調（F 鍵）一律取消，兩端都擋，畫面不會閃
        if (type == ClickType.SWAP && button == 40) { ci.cancel(); return; }
        // 拿著配件右鍵點槍：裝上配件（換下來的舊配件留在游標上）。兩端都取消原版的右鍵，伺服器負責真正裝上
        if (type == ClickType.PICKUP && button == 1 && com.provinggrounds.modes.br.AttachmentClick.handle((AbstractContainerMenu) (Object) this, slotId, player)) {
            ci.cancel();
            return;
        }
        // 在其他物品介面拿取並替換自己的背包：舊背包放回另一個物品介面（而非掉在地上或留在游標／自己背包）
        if (player instanceof ServerPlayer sp
                && com.provinggrounds.modes.br.Backpack.handleContainerClick((AbstractContainerMenu) (Object) this, slotId, button, type, sp)) {
            ci.cancel();
            return;
        }
        if (!(player instanceof ServerPlayer sp) || !HotbarRules.active(sp)) return;
        HotbarRules.Snapshot[] out = new HotbarRules.Snapshot[1];
        if (HotbarRules.beforeClick((AbstractContainerMenu) (Object) this, slotId, type, sp, out)) {
            ci.cancel();
            return;
        }
        provinggrounds$snapshot.set(out[0]);
    }

    @Inject(method = { "clicked", "m_150399_" }, at = @At("RETURN"), remap = false)
    private void provinggrounds$afterClick(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        HotbarRules.Snapshot snap = provinggrounds$snapshot.get();
        provinggrounds$snapshot.remove();
        if (snap != null && player instanceof ServerPlayer sp) HotbarRules.afterClick((AbstractContainerMenu) (Object) this, slotId, sp, snap);
    }
}
