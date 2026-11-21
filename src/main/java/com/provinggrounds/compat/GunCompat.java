package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.events.ArmorEvents;
import com.provinggrounds.modes.br.BRDowned;
import net.minecraftforge.fml.ModList;

/**
 * 檔案: GunCompat.java
 * 目的: 在模組設定階段挑選並啟用槍械適配層（有裝哪個槍械模組就用哪個）。
 *       倒地中的玩家不能開槍、近戰、換彈。
 */
public final class GunCompat {

    private GunCompat() {}

    public static void init() {
        if (ModList.get().isLoaded("tacz")) {
            GunAdapter adapter = new TaczGunAdapter();
            if (adapter.registerBlocker(BRDowned::isDowned)) {
                ProvingGroundsMod.LOGGER.info("Gun adapter enabled: {}", adapter.name());
            }
            adapter.registerHitListener(ArmorEvents::recordHit); // 防彈：記錄爆頭

        } else {
            ProvingGroundsMod.LOGGER.info("No supported gun mod loaded; gun adapter disabled.");
        }
    }
}
