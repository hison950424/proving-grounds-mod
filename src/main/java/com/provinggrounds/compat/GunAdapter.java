package com.provinggrounds.compat;

import net.minecraft.world.entity.LivingEntity;

import java.util.function.Predicate;

/**
 * 檔案: GunAdapter.java
 * 目的: 槍械適配層介面。核心程式只認這個介面，不直接 import 任何槍械模組。
 *       目前實作：TaczGunAdapter（TACZ）。之後換成自製槍械時，新增一個實作即可。
 */
public interface GunAdapter {

    /** 適配的槍械系統名稱（記錄用）。 */
    String name();

    /**
     * 註冊攔截：當 blocked 對開槍者回傳 true 時，取消開槍、近戰、換彈。
     * @return 是否成功掛上
     */
    boolean registerBlocker(Predicate<LivingEntity> blocked);
}
