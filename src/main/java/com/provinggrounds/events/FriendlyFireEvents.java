package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.LobbyManager;
import com.provinggrounds.core.TeamManager;
import com.provinggrounds.special.SpecialHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: FriendlyFireEvents.java
 * 目的: 友軍誤傷與爆炸自傷規則（計畫書 9）：
 *   - TeamManager 永遠把原版隊伍設為 allowFriendlyFire = true，避免原版把「自己的爆炸」或「隊友的投擲物」擋掉。
 *   - 任何爆炸物與投擲物：永遠可以傷害自己與隊友（不受友軍誤傷開關影響）。
 *   - 槍械子彈（與一般近戰）：看大廳「友軍誤傷」開關（所有模式皆可設定）；若任一方中了「敵我不分」，強制開啟友軍誤傷。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class FriendlyFireEvents {

    private FriendlyFireEvents() {}

    /** 槍械子彈是否允許誤傷隊友（大廳設定開，或任一方中了「敵我不分」）。 */
    public static boolean allowBulletFriendlyFire(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == victim) return true;
        if (!TeamManager.get().isSameTeam(attacker, victim)) return true;
        if (SpecialHooks.forceFriendlyFire(attacker, victim)) return true;
        return LobbyManager.get().friendlyFire();
    }

    /** 是不是爆炸物或投擲物傷害（強制有自傷與友傷）。 */
    public static boolean isExplosionOrThrowable(DamageSource src) {
        if (src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypes.EXPLOSION) || src.is(DamageTypes.PLAYER_EXPLOSION)) return true;
        if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.ON_FIRE) || src.is(DamageTypes.LAVA)) return true;
        if (src.is(DamageTypes.MAGIC) || src.is(DamageTypes.INDIRECT_MAGIC)) return true;
        String msg = src.getMsgId();
        if (msg != null && (msg.contains("explos") || msg.contains("grenade") || msg.contains("fire") || msg.contains("burn"))) return true;
        // 投擲物（非槍械子彈）：例如手榴彈、燃燒彈、閃光彈、雪球、箭、特殊道具投擲物
        if (!src.is(GunCompat.BULLET_DAMAGE)) {
            Entity direct = src.getDirectEntity();
            if (direct != null && direct != src.getEntity()) {
                if (direct instanceof Projectile) return true;
                String cn = direct.getClass().getName().toLowerCase(java.util.Locale.ROOT);
                if (cn.contains("throwable") || cn.contains("grenade") || cn.contains("molotov") || cn.contains("c4")) return true;
            }
        }
        return false;
    }

    /** TACZ 命中過濾器呼叫：子彈命中同隊時，若友軍誤傷關閉且沒中「敵我不分」，在扣血與命中特效前取消。 */
    public static boolean allowBulletHit(Entity target, Entity bullet) {
        if (!(target instanceof ServerPlayer victim)) return true;
        Entity owner = bullet instanceof Projectile pr ? pr.getOwner() : null;
        if (!(owner instanceof ServerPlayer attacker) || attacker == victim) return true;
        return allowBulletFriendlyFire(attacker, victim);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer attacker)) return;
        // 自己炸自己、自己燒自己永遠允許
        if (attacker == victim) return;
        // 不同隊永遠允許
        if (!TeamManager.get().isSameTeam(attacker, victim)) return;
        // 爆炸物與投擲物強制有友傷
        if (isExplosionOrThrowable(src)) return;
        // 槍械子彈（與一般近戰）：看友軍誤傷設定或「敵我不分」
        if (!allowBulletFriendlyFire(attacker, victim)) {
            event.setCanceled(true);
        }
    }
}
