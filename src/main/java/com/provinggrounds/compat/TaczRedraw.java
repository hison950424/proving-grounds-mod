package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: TaczRedraw.java
 * 目的: 翻爬後重新舉槍的等待時間修正（使用者回饋：舉槍動畫播完還不能馬上開鏡／開槍）。執行者：伺服器。
 *   原因：翻爬完客戶端叫 TACZ「重新拿出槍」，TACZ 伺服器把「收起上一把槍的時間」也算進等待
 *   （LivingEntityDrawGun.draw：drawTimestamp = 現在 + currentPutAwayTimeS），但槍根本沒換、畫面也只播拿槍動畫，
 *   所以動畫結束後還要多等一段收槍時間。
 *   修法：翻爬開始時記下這位玩家（expect），之後伺服器收到他的 GunDrawEvent 時，把 drawTimestamp 改成「現在」，
 *   等待時間就只剩拿槍本身，跟動畫一致。只用反射（TACZ 只能在 compat/ 存取）。
 */
public final class TaczRedraw {

    /** 玩家 → 有效期限（System.currentTimeMillis）。 */
    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();

    private static Method fromLiving, getDataHolder, getEntity, getLogicalSide;
    private static Field drawTimestamp;
    private static boolean ready;

    private TaczRedraw() {}

    /** 翻爬開始時呼叫：接下來 validMs 毫秒內這位玩家的下一次拿槍不算收槍時間。 */
    public static void expect(ServerPlayer p, long validMs) {
        if (ready) PENDING.put(p.getUUID(), System.currentTimeMillis() + validMs);
    }

    /** GunCompat.init（有裝 TACZ 時）呼叫一次。 */
    @SuppressWarnings("unchecked")
    static void register() {
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            fromLiving = op.getMethod("fromLivingEntity", LivingEntity.class);
            getDataHolder = op.getMethod("getDataHolder");
            drawTimestamp = Class.forName("com.tacz.guns.entity.shooter.ShooterDataHolder").getField("drawTimestamp");
            Class<?> raw = Class.forName("com.tacz.guns.api.event.common.GunDrawEvent");
            getEntity = raw.getMethod("getEntity");
            getLogicalSide = raw.getMethod("getLogicalSide");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) raw, TaczRedraw::onDraw);
            ready = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            ProvingGroundsMod.LOGGER.warn("TACZ redraw fix disabled: {}", e.toString());
        }
    }

    private static void onDraw(Event event) {
        if (PENDING.isEmpty()) return;
        try {
            if (!(getEntity.invoke(event) instanceof ServerPlayer p)) return;
            if (!"SERVER".equals(String.valueOf(getLogicalSide.invoke(event)))) return;
            Long until = PENDING.remove(p.getUUID());
            if (until == null || System.currentTimeMillis() > until) return;
            Object operator = fromLiving.invoke(null, p);
            Object data = operator == null ? null : getDataHolder.invoke(operator);
            if (data != null) drawTimestamp.setLong(data, System.currentTimeMillis()); // 不算收槍時間
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
