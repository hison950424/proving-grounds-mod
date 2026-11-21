package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * 檔案: TaczGunAdapter.java
 * 目的: TACZ 的槍械適配實作。用反射掛事件，不在編譯期依賴 TACZ（沒裝 TACZ 也能編譯、執行）。
 *       攔截的事件（客戶端與伺服器都會觸發，兩邊都取消才不會有開槍動畫殘留）：
 *         com.tacz.guns.api.event.common.GunShootEvent  開槍
 *         com.tacz.guns.api.event.common.GunMeleeEvent  槍托近戰
 *         com.tacz.guns.api.event.common.GunReloadEvent 換彈
 */
public class TaczGunAdapter implements GunAdapter {

    private static final String[] EVENTS = {
        "com.tacz.guns.api.event.common.GunShootEvent",
        "com.tacz.guns.api.event.common.GunMeleeEvent",
        "com.tacz.guns.api.event.common.GunReloadEvent",
    };

    @Override
    public String name() { return "TACZ"; }

    @Override
    public boolean registerBlocker(Predicate<LivingEntity> blocked) {
        int hooked = 0;
        for (String className : EVENTS) {
            try {
                Class<?> raw = Class.forName(className);
                if (!Event.class.isAssignableFrom(raw)) continue;
                Method shooterGetter = findShooterGetter(raw);
                if (shooterGetter == null) {
                    ProvingGroundsMod.LOGGER.warn("TACZ adapter: {} has no shooter getter, skipped.", className);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<Event> type = (Class<Event>) raw;
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, type, event -> {
                    if (!event.isCancelable()) return;
                    try {
                        Object shooter = shooterGetter.invoke(event);
                        if (shooter instanceof LivingEntity living && blocked.test(living)) event.setCanceled(true);
                    } catch (ReflectiveOperationException ignored) {
                        // 取不到開槍者就不攔截
                    }
                });
                hooked++;
            } catch (ClassNotFoundException e) {
                ProvingGroundsMod.LOGGER.warn("TACZ adapter: {} not found (different TACZ version?).", className);
            }
        }
        ProvingGroundsMod.LOGGER.info("TACZ adapter: hooked {} / {} events.", hooked, EVENTS.length);
        return hooked > 0;
    }

    /** 找「回傳 LivingEntity、沒有參數」的方法（TACZ 1.1.x 是 getShooter）。 */
    private static Method findShooterGetter(Class<?> type) {
        try {
            Method m = type.getMethod("getShooter");
            if (LivingEntity.class.isAssignableFrom(m.getReturnType())) return m;
        } catch (NoSuchMethodException ignored) {
            // 往下找
        }
        for (Method m : type.getMethods()) {
            if (m.getParameterCount() == 0 && LivingEntity.class.isAssignableFrom(m.getReturnType())) return m;
        }
        return null;
    }
}
