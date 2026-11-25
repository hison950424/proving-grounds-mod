package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.world.entity.Display;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: LegacyCleanup.java
 * 目的: 清掉已經移除的功能留在世界裡的東西（伺服器端，實體載入時檢查）。
 *   跑酷開始／結束方塊（計畫書 6 D1）有嚴重 BUG，已整個移除：方塊本身會自動消失（物品不存在了），
 *   但開始方塊上方的排行榜文字（TextDisplay，標籤 pg_parkour_board，文字開頭「跑酷排行榜」）會留著，這裡在它載入時刪掉。
 *   注意：跑庫塔（venue/Parkour）的排行榜也用同一個標籤，但文字是「跑庫塔排行榜」，不會被刪。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class LegacyCleanup {

    private LegacyCleanup() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Display.TextDisplay t) || !t.getTags().contains("pg_parkour_board")) return;
        String text = t.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getString("text");
        if (text.contains("跑酷排行榜")) event.setCanceled(true);
    }
}
