package com.provinggrounds.modes.br;

import com.provinggrounds.compat.GunCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 檔案: AttachmentClick.java
 * 目的: 在背包（或箱子）畫面裡拿起配件，右鍵點一把槍 → 直接把配件裝上去（不用打開 TACZ 改裝畫面）。
 *   只有這把槍能裝的配件才有作用（GunCompat.canAttach，跟配件高亮用同一套判斷）；不能裝的照原版右鍵處理。
 *   同類型位置原本有配件時，舊配件換到游標上。兩端都會呼叫：客戶端只取消原版右鍵（避免畫面閃一下），伺服器真正裝上。
 */
public final class AttachmentClick {

    private AttachmentClick() {}

    /** 回傳 true = 已處理（呼叫端取消原版點擊）。 */
    public static boolean handle(AbstractContainerMenu menu, int slotId, Player player) {
        if (slotId < 0 || slotId >= menu.slots.size()) return false;
        ItemStack carried = menu.getCarried();
        Slot slot = menu.slots.get(slotId);
        ItemStack gun = slot.getItem();
        if (carried.isEmpty() || gun.isEmpty() || !slot.mayPickup(player)) return false;
        if (!GunCompat.isAttachment(carried) || !GunCompat.canAttach(gun, carried)) return false;
        if (!(player instanceof ServerPlayer sp)) return true; // 客戶端：只取消原版右鍵，等伺服器結果
        ItemStack old = GunCompat.installAttachment(sp, gun, carried);
        if (old == null) {
            sp.displayClientMessage(Component.literal("這把槍現在不能改裝").withStyle(net.minecraft.ChatFormatting.RED), true);
            menu.sendAllDataToRemote();
            return true;
        }
        carried.shrink(1);
        if (!old.isEmpty()) {
            if (carried.isEmpty()) menu.setCarried(old);
            else if (!sp.getInventory().add(old)) HotbarRules.dropAtFeet(sp, old); // 游標上還有同樣的配件：舊的放進背包
        }
        slot.setChanged();
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.6f, 1.4f);
        menu.broadcastChanges();
        menu.sendAllDataToRemote();
        return true;
    }
}
