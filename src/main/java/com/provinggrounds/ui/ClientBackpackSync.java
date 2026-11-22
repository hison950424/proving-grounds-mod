package com.provinggrounds.ui;

import com.provinggrounds.modes.br.Backpack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 檔案: ClientBackpackSync.java
 * 目的: 收到 BackpackSyncPacket 時更新自己的背包欄（只在客戶端）。
 *   放在獨立的客戶端類別：伺服器載入封包類別時不能碰到 Minecraft／LocalPlayer。
 */
public final class ClientBackpackSync {

    private ClientBackpackSync() {}

    public static void apply(ItemStack stack) {
        if (Minecraft.getInstance().player != null) Backpack.container(Minecraft.getInstance().player).setItem(0, stack);
    }
}
