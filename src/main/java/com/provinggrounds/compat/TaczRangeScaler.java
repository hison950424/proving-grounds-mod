package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * 檔案: TaczRangeScaler.java
 * 目的: 依槍種放大 TACZ 子彈最遠飛行射程（配合雙端 32 區塊／512 格追蹤與渲染距離）：
 *   - 狙擊槍（sniper）／重武器（rpg）：512 格
 *   - 機槍（mg）：256 格
 *   - 步槍（rifle）：220 格
 *   - 衝鋒槍（smg）：120 格
 *   - 手槍（pistol）：80 格
 *   - 散彈槍（shotgun）：48 格
 *   僅透過反射操作 EntityKineticBullet.life（保留 TACZ 原生距離傷害衰減與彈道下墜），符合 checkGunModIsolation 隔離規則。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class TaczRangeScaler {

    private static final String BULLET_CLASS = "com.tacz.guns.entity.EntityKineticBullet";
    private static Field lifeField;
    private static boolean fieldAttempted;

    private TaczRangeScaler() {}

    /** 依槍種回傳目標最遠射程（格）。 */
    public static double targetRangeForGunType(String gunType) {
        if (gunType == null) return 160.0;
        return switch (gunType.toLowerCase(java.util.Locale.ROOT)) {
            case "sniper", "rpg" -> 512.0;
            case "mg" -> 256.0;
            case "rifle" -> 220.0;
            case "smg" -> 120.0;
            case "pistol" -> 80.0;
            case "shotgun" -> 48.0;
            default -> 160.0;
        };
    }

    @SubscribeEvent
    public static void onBulletSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (!BULLET_CLASS.equals(entity.getClass().getName())) return;
        Field lf = resolveLifeField(entity.getClass());
        if (lf == null) return;

        String gunType = null;
        if (entity instanceof Projectile proj && proj.getOwner() instanceof LivingEntity shooter) {
            gunType = GunCompat.gunType(shooter.getMainHandItem());
        }
        double targetRange = targetRangeForGunType(gunType);
        double speedPerTick = Math.max(1.0, entity.getDeltaMovement().length());
        int wantLife = Math.min(300, Math.max(10, (int) Math.ceil(targetRange / speedPerTick) + 2));

        try {
            int curLife = lf.getInt(entity);
            if (wantLife > curLife) {
                lf.setInt(entity, wantLife);
            }
        } catch (IllegalAccessException | RuntimeException ignored) {}
    }

    private static Field resolveLifeField(Class<?> cls) {
        if (fieldAttempted) return lifeField;
        fieldAttempted = true;
        try {
            Field f = cls.getDeclaredField("life");
            f.setAccessible(true);
            lifeField = f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ProvingGroundsMod.LOGGER.debug("TACZ bullet life field unavailable: {}", e.toString());
        }
        return lifeField;
    }
}
