package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * 檔案: TaczGunAdapter.java
 * 目的: TACZ 的槍械適配實作。用反射掛事件，不在編譯期依賴 TACZ（沒裝 TACZ 也能編譯、執行）。
 *       攔截的事件（客戶端與伺服器都會觸發，兩邊都取消才不會有開槍動畫殘留）：
 *         com.tacz.guns.api.event.common.GunShootEvent  開槍
 *         com.tacz.guns.api.event.common.GunMeleeEvent  槍托近戰
 *         com.tacz.guns.api.event.common.GunReloadEvent 換彈
 *       另外監聽 EntityHurtByGunEvent.Pre 取得爆頭判定（防彈計算用）。
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

    /**
     * 掛 EntityHurtByGunEvent.Pre（TACZ 在扣血前觸發，帶有爆頭判定）。
     * 用最低優先度、且不接收已取消的事件，拿到其他模組修改後的最終爆頭結果。
     */
    @Override
    public boolean registerHitListener(HitListener listener) {
        String className = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre";
        try {
            Class<?> raw = Class.forName(className);
            if (!Event.class.isAssignableFrom(raw)) return false;
            Method getTarget = raw.getMethod("getHurtEntity");
            Method getBullet = raw.getMethod("getBullet");
            Method isHeadShot = raw.getMethod("isHeadShot");
            Method getSide = raw.getMethod("getLogicalSide");
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) raw;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, event -> {
                try {
                    if (getSide.invoke(event) != LogicalSide.SERVER) return;
                    if (getTarget.invoke(event) instanceof Entity target && getBullet.invoke(event) instanceof Entity bullet) {
                        listener.onHit(target, bullet, (Boolean) isHeadShot.invoke(event));
                    }
                } catch (ReflectiveOperationException | ClassCastException ignored) {
                    // 取不到資料就當作身體中彈
                }
            });
            ProvingGroundsMod.LOGGER.info("TACZ adapter: hit listener hooked.");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            ProvingGroundsMod.LOGGER.warn("TACZ adapter: cannot hook {} ({}); headshot armor split disabled.", className, e.toString());
            return false;
        }
    }

    /**
     * 掛 AmmoHitBlockEvent（伺服器端，子彈打到方塊）。用最高優先度，
     * 才能在 TACZ 自己打破玻璃（同一個事件的另一個監聽者）之前記下方塊原本的狀態。
     */
    @Override
    public boolean registerBlockHitListener(BlockHitListener listener) {
        String className = "com.tacz.guns.api.event.server.AmmoHitBlockEvent";
        try {
            Class<?> raw = Class.forName(className);
            if (!Event.class.isAssignableFrom(raw)) return false;
            Method getLevel = raw.getMethod("getLevel");
            Method getState = raw.getMethod("getState");
            Method getHit = raw.getMethod("getHitResult");
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) raw;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, type, event -> {
                try {
                    if (getLevel.invoke(event) instanceof net.minecraft.world.level.Level level
                            && getState.invoke(event) instanceof net.minecraft.world.level.block.state.BlockState state
                            && getHit.invoke(event) instanceof net.minecraft.world.phys.BlockHitResult hit) {
                        listener.onBlockHit(level, hit.getBlockPos(), state);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 取不到資料就不記錄
                }
            });
            ProvingGroundsMod.LOGGER.info("TACZ adapter: block hit listener hooked.");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            ProvingGroundsMod.LOGGER.warn("TACZ adapter: cannot hook {} ({}); broken glass will not be restored.", className, e.toString());
            return false;
        }
    }

    private final java.util.Map<net.minecraft.resources.ResourceLocation, GunClass> classCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 槍的分類：物品 NBT 有 GunId 就是 TACZ 槍，再查 TACZ 槍械資料的類型（type 為 "pistol" 是手槍，其他都算大型槍）。
     * 結果依 GunId 快取（槍械包重載時類型不會變）。
     */
    @Override
    public GunClass classify(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || stack.getTag() == null || !stack.getTag().contains("GunId", 8)) return GunClass.NONE;
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(stack.getTag().getString("GunId"));
        if (id == null) return GunClass.NONE;
        try {
            if (com.provinggrounds.config.PGConfig.SERVER.pistolGuns.get().contains(id.toString())) return GunClass.PISTOL; // 設定 pistol_guns
        } catch (IllegalStateException ignored) {
            // 設定還沒載入：照槍械包的分類
        }
        return classCache.computeIfAbsent(id, TaczGunAdapter::lookupClass);
    }

    private static GunClass lookupClass(net.minecraft.resources.ResourceLocation gunId) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class).invoke(null, gunId);
            if (opt instanceof java.util.Optional<?> o && o.isPresent()) {
                Object type = o.get().getClass().getMethod("getType").invoke(o.get());
                return "pistol".equals(type) ? GunClass.PISTOL : GunClass.LARGE;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 查不到類型就當大型槍
        }
        return GunClass.LARGE;
    }

    // ── 配件（第三份計畫書 D）─────────────────────────────────────────────────
    // 用 TACZ 自己的相容資料：IAttachment.getIAttachmentOrNull 判斷是不是配件，IGun#allowAttachment(槍, 配件) 判斷能不能裝。
    // 用反射呼叫（不在編譯期依賴 TACZ）；方法只查一次。結果依「槍 ID｜配件 ID」快取，最多 1024 筆。

    private static Method getIAttachment, getAttachmentId, getIGun, allowAttachment, getGunId;
    private static boolean attachmentApiReady, attachmentApiFailed;

    private static synchronized boolean attachmentApi() {
        if (attachmentApiReady) return true;
        if (attachmentApiFailed) return false;
        try {
            Class<?> att = Class.forName("com.tacz.guns.api.item.IAttachment");
            Class<?> gun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> stack = net.minecraft.world.item.ItemStack.class;
            getIAttachment = att.getMethod("getIAttachmentOrNull", stack);
            getAttachmentId = att.getMethod("getAttachmentId", stack);
            getIGun = gun.getMethod("getIGunOrNull", stack);
            allowAttachment = gun.getMethod("allowAttachment", stack, stack);
            getGunId = gun.getMethod("getGunId", stack);
            attachmentApiReady = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            attachmentApiFailed = true;
            com.provinggrounds.ProvingGroundsMod.LOGGER.warn("TACZ adapter: attachment API not found ({}); attachment highlight disabled.", e.toString());
            return false;
        }
    }

    private final java.util.Map<String, Boolean> attachCache = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> e) { return size() > 1024; }
        });

    @Override
    public boolean isAttachment(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !attachmentApi()) return false;
        try {
            return getIAttachment.invoke(null, stack) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean canAttach(net.minecraft.world.item.ItemStack gunStack, net.minecraft.world.item.ItemStack attachment) {
        if (gunStack.isEmpty() || attachment.isEmpty() || !attachmentApi()) return false;
        try {
            Object iGun = getIGun.invoke(null, gunStack);
            Object iAtt = getIAttachment.invoke(null, attachment);
            if (iGun == null || iAtt == null) return false;
            String key = getGunId.invoke(iGun, gunStack) + "|" + getAttachmentId.invoke(iAtt, attachment);
            Boolean cached = attachCache.get(key);
            if (cached != null) return cached;
            boolean ok = Boolean.TRUE.equals(allowAttachment.invoke(iGun, gunStack, attachment));
            attachCache.put(key, ok);
            return ok;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
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
