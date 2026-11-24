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
     * 精準命中：掛 EntityHurtByGunEvent.Pre（最高優先度），過濾器回傳 false 就取消這次傷害。
     */
    @Override
    public boolean registerHitFilter(HitFilter filter) {
        String className = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre";
        try {
            Class<?> raw = Class.forName(className);
            if (!Event.class.isAssignableFrom(raw)) return false;
            Method getTarget = raw.getMethod("getHurtEntity");
            Method getBullet = raw.getMethod("getBullet");
            Method getSide = raw.getMethod("getLogicalSide");
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) raw;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, type, event -> {
                if (!event.isCancelable()) return;
                try {
                    if (getSide.invoke(event) != LogicalSide.SERVER) return;
                    if (getTarget.invoke(event) instanceof Entity target && getBullet.invoke(event) instanceof Entity bullet
                            && !filter.accept(target, bullet)) {
                        event.setCanceled(true);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 取不到資料就照原本的命中
                }
            });
            ProvingGroundsMod.LOGGER.info("TACZ adapter: hit filter hooked.");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            ProvingGroundsMod.LOGGER.warn("TACZ adapter: cannot hook {} ({}); precise hitbox disabled.", className, e.toString());
            return false;
        }
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

    // ── 槍械平衡（GunBalance）────────────────────────────────────────────────
    // 讀 TACZ 的槍械資料（類型、射速、傷害、彈丸數、爆頭倍率、距離減傷表），在 EntityHurtByGunEvent.Pre 改這一發的傷害。

    private final java.util.Map<net.minecraft.resources.ResourceLocation, java.util.Optional<com.provinggrounds.core.GunBalance.Stats>> statsCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public com.provinggrounds.core.GunBalance.Stats gunStats(net.minecraft.resources.ResourceLocation gunId) {
        return statsCache.computeIfAbsent(gunId, TaczGunAdapter::readStats).orElse(null);
    }

    private static java.util.Optional<com.provinggrounds.core.GunBalance.Stats> readStats(net.minecraft.resources.ResourceLocation gunId) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class).invoke(null, gunId);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) return java.util.Optional.empty();
            Object index = o.get();
            String type = String.valueOf(index.getClass().getMethod("getType").invoke(index));
            Object gunData = index.getClass().getMethod("getGunData").invoke(index);
            int rpm = ((Number) gunData.getClass().getMethod("getRoundsPerMinute").invoke(gunData)).intValue();
            int mag = ((Number) gunData.getClass().getMethod("getAmmoAmount").invoke(gunData)).intValue();
            Object bullet = gunData.getClass().getMethod("getBulletData").invoke(gunData);
            float damage = ((Number) bullet.getClass().getMethod("getDamageAmount").invoke(bullet)).floatValue();
            int amount = ((Number) bullet.getClass().getMethod("getBulletAmount").invoke(bullet)).intValue();
            float head = 1.0f;
            java.util.List<float[]> falloff = new java.util.ArrayList<>();
            Object extra = bullet.getClass().getMethod("getExtraDamage").invoke(bullet);
            if (extra != null) {
                head = ((Number) extra.getClass().getMethod("getHeadShotMultiplier").invoke(extra)).floatValue();
                Object list = extra.getClass().getMethod("getDamageAdjust").invoke(extra);
                if (list instanceof java.util.List<?> l) {
                    for (Object pair : l) {
                        float d = ((Number) pair.getClass().getMethod("getDistance").invoke(pair)).floatValue();
                        float v = ((Number) pair.getClass().getMethod("getDamage").invoke(pair)).floatValue();
                        falloff.add(new float[] { d, v });
                    }
                }
            }
            return java.util.Optional.of(new com.provinggrounds.core.GunBalance.Stats(type, rpm, damage, amount, head, falloff, mag));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public boolean registerDamageBalancer() {
        String className = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre";
        try {
            Class<?> raw = Class.forName(className);
            if (!Event.class.isAssignableFrom(raw)) return false;
            Method getSide = raw.getMethod("getLogicalSide");
            Method getGunId = raw.getMethod("getGunId");
            Method getAttacker = raw.getMethod("getAttacker");
            Method getTarget = raw.getMethod("getHurtEntity");
            Method getBase = raw.getMethod("getBaseAmount");
            Method setBase = raw.getMethod("setBaseAmount", float.class);
            Method setHead = raw.getMethod("setHeadshotMultiplier", float.class);
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) raw;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, type, event -> {
                try {
                    if (getSide.invoke(event) != LogicalSide.SERVER || !com.provinggrounds.core.GunBalance.enabled()) return;
                    if (!(getGunId.invoke(event) instanceof net.minecraft.resources.ResourceLocation gunId)) return;
                    Entity target = (Entity) getTarget.invoke(event);
                    Entity attacker = (Entity) getAttacker.invoke(event);
                    double dist = attacker != null && target != null ? attacker.distanceTo(target) : 0;
                    com.provinggrounds.core.GunBalance.Stats stats = gunStats(gunId);
                    float base = ((Number) getBase.invoke(event)).floatValue();
                    setBase.invoke(event, com.provinggrounds.core.GunBalance.adjust(gunId, stats, base, dist));
                    float head = com.provinggrounds.core.GunBalance.headMultiplier(gunId, stats);
                    if (head > 0) setHead.invoke(event, head);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 取不到資料就照原本的傷害
                }
            });
            ProvingGroundsMod.LOGGER.info("TACZ adapter: gun damage balancer hooked.");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            ProvingGroundsMod.LOGGER.warn("TACZ adapter: cannot hook damage balancer ({}).", e.toString());
            return false;
        }
    }

    // ── 槍械模式（依類型換槍）────────────────────────────────────────────────

    @Override
    public String gunType(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.resources.ResourceLocation id = gunId(stack);
        if (id == null) return null;
        com.provinggrounds.core.GunBalance.Stats s = gunStats(id);
        return s == null ? null : s.type();
    }

    private volatile java.util.Map<String, java.util.List<net.minecraft.resources.ResourceLocation>> byType;

    @Override
    public java.util.List<net.minecraft.resources.ResourceLocation> gunsOfType(String type) {
        if (byType == null) {
            java.util.Map<String, java.util.List<net.minecraft.resources.ResourceLocation>> map = new java.util.HashMap<>();
            try {
                Object set = Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getAllCommonGunIndex").invoke(null);
                for (Object o : (java.util.Set<?>) set) {
                    java.util.Map.Entry<?, ?> e = (java.util.Map.Entry<?, ?>) o;
                    if (!(e.getKey() instanceof net.minecraft.resources.ResourceLocation id)) continue;
                    String t = String.valueOf(e.getValue().getClass().getMethod("getType").invoke(e.getValue()));
                    map.computeIfAbsent(t, k -> new java.util.ArrayList<>()).add(id);
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                ProvingGroundsMod.LOGGER.warn("TACZ adapter: cannot list guns ({})", ex.toString());
            }
            byType = map;
        }
        return byType.getOrDefault(type, java.util.List.of());
    }

    @Override
    public net.minecraft.world.item.ItemStack createGun(net.minecraft.resources.ResourceLocation gunId) {
        try {
            Class<?> b = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            Object builder = b.getMethod("create").invoke(null);
            builder = b.getMethod("setId", net.minecraft.resources.ResourceLocation.class).invoke(builder, gunId);
            Object opt = Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class).invoke(null, gunId);
            if (opt instanceof java.util.Optional<?> o && o.isPresent()) {
                Object data = o.get().getClass().getMethod("getGunData").invoke(o.get());
                Object modes = data.getClass().getMethod("getFireModeSet").invoke(data);
                if (modes instanceof java.util.List<?> l && !l.isEmpty()) {
                    builder = b.getMethod("setFireMode", Class.forName("com.tacz.guns.api.item.gun.FireMode")).invoke(builder, l.get(0));
                }
            }
            Object stack = b.getMethod("build").invoke(builder);
            return stack instanceof net.minecraft.world.item.ItemStack s ? s : net.minecraft.world.item.ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

    @Override
    public net.minecraft.world.item.ItemStack createLoadedGun(net.minecraft.resources.ResourceLocation gunId, @javax.annotation.Nullable String fireMode) {
        net.minecraft.world.item.ItemStack stack = createGun(gunId);
        if (stack.isEmpty()) return stack;
        if (fireMode != null && !fireMode.isEmpty()) stack.getOrCreateTag().putString("GunFireMode", fireMode);
        fillAmmo(stack);
        return stack;
    }

    @Override
    public void fillAmmo(net.minecraft.world.item.ItemStack gun) {
        net.minecraft.resources.ResourceLocation id = gunId(gun);
        if (id == null) return;
        com.provinggrounds.core.GunBalance.Stats s = gunStats(id);
        int mag = s == null ? 0 : s.magazine();
        net.minecraft.nbt.CompoundTag tag = gun.getOrCreateTag();
        // 膛內一發＋彈匣（容量 - 1），同舊版資料包的寫法
        tag.putInt("GunCurrentAmmoCount", Math.max(0, mag - 1));
        tag.putBoolean("HasBulletInBarrel", true);
    }

    // ── 消音器（受擊指示器用）────────────────────────────────────────────────

    private static Object muzzleType;
    private static boolean silenceFailed;

    @Override
    public boolean isSilenced(net.minecraft.world.item.ItemStack gunStack) {
        if (silenceFailed || !installApi()) return false;
        try {
            if (muzzleType == null) {
                Class<?> type = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
                muzzleType = type.getMethod("valueOf", String.class).invoke(null, "MUZZLE");
            }
            Object iGun = getIGun.invoke(null, gunStack);
            if (iGun == null) return false;
            Object att = getAttachmentOfType.invoke(iGun, gunStack, muzzleType);
            if (!(att instanceof net.minecraft.world.item.ItemStack a) || a.isEmpty()) return false;
            Object iAtt = getIAttachment.invoke(null, a);
            if (iAtt == null) return false;
            String id = String.valueOf(getAttachmentId.invoke(iAtt, a)).toLowerCase(java.util.Locale.ROOT);
            return id.contains("silencer") || id.contains("suppressor");
        } catch (ReflectiveOperationException | RuntimeException e) {
            silenceFailed = true;
            return false;
        }
    }

    // ── 拿配件右鍵槍枝裝上 ──────────────────────────────────────────────────
    // 跟 TACZ 改裝畫面（ClientMessageRefitGun）同樣的步驟：檢查配件鎖與相容 → 取出同類型的舊配件 → 裝上新配件
    // → 通知配件屬性變更；換的是擴充彈匣時把槍裡的子彈退出來（TACZ 原本的行為）。

    private static Method hasAttachmentLock, getAttachmentType, getAttachmentOfType, installAttachmentM, postChangeEvent, dropAllAmmo;
    private static boolean installReady, installFailed;

    private static synchronized boolean installApi() {
        if (installReady) return true;
        if (installFailed || !attachmentApi()) return false;
        try {
            Class<?> gun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> att = Class.forName("com.tacz.guns.api.item.IAttachment");
            Class<?> type = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            Class<?> stack = net.minecraft.world.item.ItemStack.class;
            hasAttachmentLock = gun.getMethod("hasAttachmentLock", stack);
            getAttachmentType = att.getMethod("getType", stack);
            getAttachmentOfType = gun.getMethod("getAttachment", stack, type);
            installAttachmentM = gun.getMethod("installAttachment", stack, stack);
            postChangeEvent = Class.forName("com.tacz.guns.resource.modifier.AttachmentPropertyManager")
                .getMethod("postChangeEvent", net.minecraft.world.entity.LivingEntity.class, stack);
            dropAllAmmo = gun.getMethod("dropAllAmmo", net.minecraft.world.entity.player.Player.class, stack);
            installReady = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            installFailed = true;
            ProvingGroundsMod.LOGGER.warn("TACZ adapter: install attachment API not found ({}); right-click install disabled.", e.toString());
            return false;
        }
    }

    @Override
    public net.minecraft.world.item.ItemStack installAttachment(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.item.ItemStack gunStack, net.minecraft.world.item.ItemStack attachment) {
        if (gunStack.isEmpty() || attachment.isEmpty() || !installApi()) return null;
        try {
            Object iGun = getIGun.invoke(null, gunStack);
            Object iAtt = getIAttachment.invoke(null, attachment);
            if (iGun == null || iAtt == null) return null;
            if (Boolean.TRUE.equals(hasAttachmentLock.invoke(iGun, gunStack))) return null;
            if (!Boolean.TRUE.equals(allowAttachment.invoke(iGun, gunStack, attachment))) return null;
            Object type = getAttachmentType.invoke(iAtt, attachment);
            Object old = getAttachmentOfType.invoke(iGun, gunStack, type);
            installAttachmentM.invoke(iGun, gunStack, attachment.copyWithCount(1));
            postChangeEvent.invoke(null, player, gunStack);
            if ("EXTENDED_MAG".equals(String.valueOf(type))) dropAllAmmo.invoke(iGun, player, gunStack);
            return old instanceof net.minecraft.world.item.ItemStack s ? s.copy() : net.minecraft.world.item.ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ── 假槍聲（第四份計畫書 33 號）────────────────────────────────────────────
    // 用 TACZ 自己的封包 ServerMessageSound（槍 ID、聲音名稱 "shoot"）綁在一個實體上，送給追蹤那個實體的玩家。

    @Override
    public net.minecraft.resources.ResourceLocation gunId(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || stack.getTag() == null || !stack.getTag().contains("GunId", 8)) return null;
        return net.minecraft.resources.ResourceLocation.tryParse(stack.getTag().getString("GunId"));
    }

    private static Method sendSoundToNearby, getGunDisplayId;
    private static String shoot3p;
    private static boolean soundFailed;

    /**
     * 跟 TACZ 自己開槍時送給「其他玩家」的槍聲完全一樣：SoundManager.sendSoundToNearby(音源, 距離, 槍 ID, 槍外觀 ID, "shoot_3p", 音量, 音調)。
     * 客戶端用槍外觀裡的第三人稱槍聲播放；音源必須是活的實體（假槍聲用看不見的盔甲座）。
     */
    @Override
    public boolean playGunSound(net.minecraft.world.entity.Entity at, net.minecraft.world.item.ItemStack gun) {
        if (!(at instanceof net.minecraft.world.entity.LivingEntity living) || soundFailed) return false;
        net.minecraft.resources.ResourceLocation gunId = gunId(gun);
        if (gunId == null) return false;
        try {
            if (sendSoundToNearby == null) {
                Class<?> sm = Class.forName("com.tacz.guns.sound.SoundManager");
                sendSoundToNearby = sm.getMethod("sendSoundToNearby", net.minecraft.world.entity.LivingEntity.class, int.class,
                    net.minecraft.resources.ResourceLocation.class, net.minecraft.resources.ResourceLocation.class, String.class, float.class, float.class);
                shoot3p = (String) sm.getField("SHOOT_3P_SOUND").get(null);
                getGunDisplayId = Class.forName("com.tacz.guns.api.item.IGun").getMethod("getGunDisplayId", net.minecraft.world.item.ItemStack.class);
            }
            Object iGun = getIGunOrNull(gun);
            Object display = iGun == null ? null : getGunDisplayId.invoke(iGun, gun);
            if (display == null) display = new net.minecraft.resources.ResourceLocation("tacz", "default");
            float pitch = 0.9f + at.level().getRandom().nextFloat() * 0.125f;
            sendSoundToNearby.invoke(null, living, 128, gunId, display, shoot3p, 0.8f, pitch);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            soundFailed = true;
            ProvingGroundsMod.LOGGER.warn("TACZ adapter: gun sound not available ({}); fake gunshot uses vanilla sound.", e.toString());
            return false;
        }
    }

    private static Object getIGunOrNull(net.minecraft.world.item.ItemStack stack) throws ReflectiveOperationException {
        return Class.forName("com.tacz.guns.api.item.IGun").getMethod("getIGunOrNull", net.minecraft.world.item.ItemStack.class).invoke(null, stack);
    }

    /** 找「回傳 LivingEntity、沒有參數」的方法（TACZ 1.1.x 是 getShooter）。 */
    @Override
    public boolean registerShootListener(java.util.function.Consumer<LivingEntity> listener) {
        try {
            Class<?> raw = Class.forName(EVENTS[0]);
            Method shooterGetter = findShooterGetter(raw);
            if (shooterGetter == null || !Event.class.isAssignableFrom(raw)) return false;
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) raw;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, event -> {
                try {
                    if (shooterGetter.invoke(event) instanceof LivingEntity living) listener.accept(living);
                } catch (ReflectiveOperationException ignored) {
                    // 取不到開槍者就不通知
                }
            });
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

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
