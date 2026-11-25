package com.provinggrounds.special;

import com.provinggrounds.config.PGConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 檔案: SpecialConfig.java
 * 目的: 特殊物品的所有數值（計畫書 4），在伺服器設定檔的 [special] 區段。
 *   標「占位」的是計畫書沒有指定、先用建議值的項目。
 */
public final class SpecialConfig {

    public final ForgeConfigSpec.BooleanValue enabled;
    public final ForgeConfigSpec.DoubleValue chance;
    public final ForgeConfigSpec.DoubleValue airdropChance;
    public final ForgeConfigSpec.IntValue useCooldownTicks;

    // 共用引擎
    public final ForgeConfigSpec.DoubleValue glowSeconds;
    public final ForgeConfigSpec.DoubleValue shockRadius, shockCenterDamage, shockConcussSeconds, shockKnockback;
    public final ForgeConfigSpec.DoubleValue concussStrength;
    public final ForgeConfigSpec.DoubleValue mineTriggerRadius, mineDelaySeconds, mineEffectRadius;
    public final ForgeConfigSpec.DoubleValue fakeCrateDamage, fakeCrateGlowSeconds, fakeCrateConcussSeconds;
    public final ForgeConfigSpec.BooleanValue friendlyFire;

    // 物品
    public final ForgeConfigSpec.DoubleValue blindRange, blindSpeed, blindSeconds;
    public final ForgeConfigSpec.DoubleValue adrenalineSeconds, adrenalineBonus;
    public final ForgeConfigSpec.DoubleValue cloakSeconds, cloakBonus;
    public final ForgeConfigSpec.DoubleValue springPower;
    public final ForgeConfigSpec.DoubleValue snowRange, snowSeconds;
    public final ForgeConfigSpec.IntValue snowHeight;
    public final ForgeConfigSpec.IntValue shieldDurability;
    public final ForgeConfigSpec.DoubleValue shieldChargeRange, shieldShockRadius, shieldSpeedPenalty;
    public final ForgeConfigSpec.DoubleValue windShockRadius;
    public final ForgeConfigSpec.DoubleValue landMineDamage, landMineSlowSeconds, fireMineSeconds, fireMineDamage;
    public final ForgeConfigSpec.DoubleValue smokeRadius, smokeSeconds;
    public final ForgeConfigSpec.DoubleValue totemSeconds, totemHeal, totemAbsorption, totemRegenSeconds, totemSoundVolume;
    public final ForgeConfigSpec.DoubleValue bubbleSeconds, bubbleAbsorption, bubbleSpeedBonus, bubbleSpeedSeconds;
    public final ForgeConfigSpec.DoubleValue pearlRange, pearlSeconds, pearlSpeed;
    public final ForgeConfigSpec.DoubleValue silentSeconds, silentBonus, stimSelfDamage, stimDownedFreeSeconds;
    public final ForgeConfigSpec.DoubleValue gasSeconds, gasLingerSeconds, gasPoisonSeconds;
    public final ForgeConfigSpec.DoubleValue selfReviveSeconds;
    public final ForgeConfigSpec.BooleanValue flashShieldHalved, flashShield045, flashShield50;
    public final ForgeConfigSpec.DoubleValue armorPlusBonus;
    public final ForgeConfigSpec.IntValue armorPlusMaxStacks;
    public final ForgeConfigSpec.DoubleValue shelterSeconds, shelterBonus;
    public final ForgeConfigSpec.DoubleValue radarRange, radarSeconds;
    public final ForgeConfigSpec.DoubleValue decoySeconds, decoyRevealSeconds;

    public SpecialConfig(ForgeConfigSpec.Builder b) {
        b.comment("特殊物品（計畫書 4）：特殊物品欄 1 格，按「使用特殊物品」鍵（預設 Q）使用，用完就消失").push("special");
        enabled = b.comment("特殊物品模式：關閉時箱子不會出現特殊物品、不能使用、也不顯示特殊物品欄。大廳選單（管理員）或 /pg special mode on|off 可以切換").define("enabled", true);
        chance = b.comment("每個箱子（低、中、高、空投都一樣）出現 1 件特殊物品的機率，0.1 = 約每 10 箱 1 件。出現哪一件依 data/br/loot_tables/special.json 的權重")
            .defineInRange("chance", 0.1, 0.0, 1.0);
        airdropChance = b.comment("空投箱出現 1 件特殊物品的機率（1.0 = 每個空投一定有）").defineInRange("airdrop_chance", 1.0, 0.0, 1.0);
        useCooldownTicks = b.comment("兩次使用特殊物品之間至少間隔幾 tick（防止連按）").defineInRange("use_cooldown_ticks", 10, 0, 200);

        b.comment("共用引擎").push("engine");
        glowSeconds = b.comment("發光持續秒數").defineInRange("glow_seconds", 5.0, 0.5, 120.0);
        shockRadius = b.comment("衝擊波預設半徑（格）").defineInRange("shock_radius", 3.5, 0.5, 20.0);
        shockCenterDamage = b.comment("衝擊波中心傷害（往外線性遞減到 0）").defineInRange("shock_center_damage", 20.0, 0.0, 1000.0);
        shockConcussSeconds = b.comment("衝擊波附帶的震盪秒數").defineInRange("shock_concuss_seconds", 10.0, 0.0, 120.0);
        shockKnockback = b.comment("衝擊波震飛力道（占位）").defineInRange("shock_knockback", 1.6, 0.0, 10.0);
        concussStrength = b.comment("震盪：視角晃動的最大角度（度）。原本的鍵 concuss_strength（6）已停用，改名後預設 12（計畫書 6 A16：晃動 +100%）").defineInRange("concuss_strength_v2", 12.0, 0.0, 45.0);
        mineTriggerRadius = b.comment("地雷：敵人進入幾格內觸發").defineInRange("mine_trigger_radius", 2.0, 0.5, 10.0);
        mineDelaySeconds = b.comment("地雷：觸發後幾秒產生效果").defineInRange("mine_delay_seconds", 0.5, 0.0, 10.0);
        mineEffectRadius = b.comment("地雷：效果半徑（格）").defineInRange("mine_effect_radius", 3.5, 0.5, 20.0);
        fakeCrateDamage = b.comment("假箱：打開的人受到的傷害").defineInRange("fake_crate_damage", 10.0, 0.0, 1000.0);
        fakeCrateGlowSeconds = b.comment("假箱：打開的人發光秒數（全體看得到、不上地圖）").defineInRange("fake_crate_glow_seconds", 5.0, 0.0, 120.0);
        fakeCrateConcussSeconds = b.comment("假箱：打開的人震盪秒數").defineInRange("fake_crate_concuss_seconds", 5.0, 0.0, 120.0);
        friendlyFire = b.comment("特殊物品的效果會影響自己與隊友（衝擊波、地雷、燃燒、致盲、毒氣…；擊退盾的衝撞例外）").define("friendly_fire", true);
        b.pop();

        b.comment("各物品").push("items");
        blindRange = b.comment("致盲珠：推進距離（格）").defineInRange("blind_range", 15.0, 1.0, 100.0);
        blindSpeed = b.comment("致盲珠：推進速度（格/秒；使用者回饋：原本 5 的兩倍）").defineInRange("blind_speed_v2", 10.0, 0.5, 50.0);
        blindSeconds = b.comment("致盲珠：致盲秒數").defineInRange("blind_seconds", 5.0, 0.5, 60.0);
        adrenalineSeconds = b.comment("強心劑（原腎上腺素）：持續秒數").defineInRange("adrenaline_seconds", 15.0, 1.0, 120.0);
        adrenalineBonus = b.comment("強心劑：移動速度加成（0.5 = +50%，以 BR 基本速度為基準；不和拿刀疊加；計畫書 7 C10）").defineInRange("adrenaline_bonus_v2", 0.5, 0.0, 5.0);
        stimSelfDamage = b.comment("強心劑：使用時自己受到的傷害（不會致死，至少留 1 血）").defineInRange("stim_self_damage_v1", 10.0, 0.0, 100.0);
        stimDownedFreeSeconds = b.comment("強心劑：效果期間倒地時，倒地後前幾秒可以使用任何物品（不超過強心劑剩下的時間）").defineInRange("stim_downed_free_seconds_v1", 5.0, 0.0, 60.0);
        cloakSeconds = b.comment("隱形斗篷：持續秒數").defineInRange("cloak_seconds", 5.0, 1.0, 120.0);
        cloakBonus = b.comment("隱形斗篷：移動速度加成（0.25 = +25%，以 BR 基本速度為基準；可和拿刀疊加）").defineInRange("cloak_bonus", 0.25, 0.0, 5.0);
        springPower = b.comment("彈簧鞋：衝刺力道（計畫書 7 C4：原本 1.3 約 8 格，×3 約 24 格）").defineInRange("spring_power_v2", 3.9, 0.1, 10.0);
        snowRange = b.comment("臨時雪牆：雪球最遠水平飛行距離（格）").defineInRange("snow_range", 15.0, 1.0, 100.0);
        snowHeight = b.comment("臨時雪牆：雪牆最高幾格").defineInRange("snow_height", 4, 1, 16);
        snowSeconds = b.comment("臨時雪牆：雪塊幾秒後消失").defineInRange("snow_seconds", 15.0, 1.0, 600.0);
        shieldDurability = b.comment("擊退盾：耐久度（擋下的傷害會扣耐久）").defineInRange("shield_durability", 500, 1, 100000);
        shieldChargeRange = b.comment("擊退盾：衝撞最遠距離（格）").defineInRange("shield_charge_range", 25.0, 1.0, 100.0);
        shieldShockRadius = b.comment("擊退盾：衝撞的衝擊波半徑（格）").defineInRange("shield_shock_radius", 2.5, 0.5, 20.0);
        shieldSpeedPenalty = b.comment("擊退盾：舉著盾時移動速度減少多少（0.25 = -25%；衝撞不受影響）").defineInRange("shield_speed_penalty", 0.25, 0.0, 0.95);
        windShockRadius = b.comment("擊退風暴彈：衝擊波半徑（格）").defineInRange("wind_shock_radius", 2.5, 0.5, 20.0);
        landMineDamage = b.comment("地雷：中心傷害（越外圍越低）").defineInRange("land_mine_damage", 45.0, 0.0, 1000.0);
        landMineSlowSeconds = b.comment("地雷：緩速秒數").defineInRange("land_mine_slow_seconds", 5.0, 0.0, 120.0);
        fireMineSeconds = b.comment("燃燒地雷：燃燒秒數").defineInRange("fire_mine_seconds", 5.0, 0.0, 120.0);
        fireMineDamage = b.comment("燃燒地雷：每秒燒傷").defineInRange("fire_mine_damage", 6.0, 0.0, 1000.0);
        smokeRadius = b.comment("煙幕彈：煙霧半徑（格），以自己為中心的平放圓柱（高 SMOKE_HEIGHT 格）。原本的鍵 smoke_radius_v2（5）已停用，改名後預設 8（計畫書 6 A17）").defineInRange("smoke_radius_v3", 8.0, 0.5, 20.0);
        smokeSeconds = b.comment("煙幕彈：煙霧持續秒數（占位）").defineInRange("smoke_seconds", 15.0, 1.0, 120.0);
        totemSeconds = b.comment("不死圖騰：副手有圖騰的秒數").defineInRange("totem_seconds", 15.0, 1.0, 600.0);
        totemHeal = b.comment("不死圖騰：觸發後血量回到多少").defineInRange("totem_heal", 50.0, 1.0, 1000.0);
        totemAbsorption = b.comment("不死圖騰：觸發後額外生命值 +多少（不會消失）").defineInRange("totem_absorption", 25.0, 0.0, 1000.0);
        totemRegenSeconds = b.comment("不死圖騰：觸發後恢復效果 I 幾秒").defineInRange("totem_regen_seconds", 10.0, 0.0, 120.0);
        totemSoundVolume = b.comment("不死圖騰：觸發音效音量（1.0 = 原版；0.5 = 一半）").defineInRange("totem_sound_volume", 0.5, 0.0, 1.0);
        bubbleSeconds = b.comment("護盾泡泡：等待受傷的秒數").defineInRange("bubble_seconds", 15.0, 1.0, 600.0);
        bubbleAbsorption = b.comment("護盾泡泡：觸發時獲得的額外生命值（不會消失）").defineInRange("bubble_absorption", 25.0, 0.0, 1000.0);
        bubbleSpeedBonus = b.comment("護盾泡泡：觸發後的移動速度加成（0.25 = +25%）").defineInRange("bubble_speed_bonus", 0.25, 0.0, 5.0);
        bubbleSpeedSeconds = b.comment("護盾泡泡：加速秒數").defineInRange("bubble_speed_seconds", 10.0, 0.0, 120.0);
        pearlRange = b.comment("傳送珠：最遠水平距離（格）").defineInRange("pearl_range", 35.0, 1.0, 200.0);
        pearlSeconds = b.comment("傳送珠：最長飛行秒數").defineInRange("pearl_seconds", 15.0, 1.0, 120.0);
        pearlSpeed = b.comment("傳送珠：飛行速度（格/tick，占位）").defineInRange("pearl_speed", 1.2, 0.1, 5.0);
        silentSeconds = b.comment("靜音靴：持續秒數").defineInRange("silent_seconds", 15.0, 1.0, 600.0);
        silentBonus = b.comment("靜音靴：移動速度加成（1.0 = +100%，以 BR 基本速度為基準；不和拿刀、強心劑疊加，取較大者；計畫書 7 C9）").defineInRange("silent_bonus_v1", 1.0, 0.0, 5.0);
        gasSeconds = b.comment("毒氣彈：毒氣本身持續秒數（占位）").defineInRange("gas_seconds", 10.0, 1.0, 120.0);
        gasLingerSeconds = b.comment("毒氣彈：離開毒氣後效果殘留秒數").defineInRange("gas_linger_seconds", 5.0, 0.0, 120.0);
        gasPoisonSeconds = b.comment("毒氣彈：中毒秒數").defineInRange("gas_poison_seconds", 5.0, 0.0, 120.0);
        selfReviveSeconds = b.comment("急救針：自救讀條秒數").defineInRange("self_revive_seconds", 10.0, 1.0, 120.0);
        flashShieldHalved = b.comment("內部用：是否已把 lrtactical 閃光盾的耐久（flash_shield_max_durability）減半過（計畫書 6 A21，只做一次）。改成 false 會在下次開服再減半一次")
            .define("flash_shield_durability_halved_v1", false);
        flashShield50 = b.comment("內部用：是否已把閃光盾耐久設成 50（使用者回饋，只做一次）").define("flash_shield_durability_50_v1", false);
        flashShield045 = b.comment("內部用：是否已把閃光盾耐久再改成當時的 45%（使用者回饋，只做一次）").define("flash_shield_durability_045_v1", false);
        armorPlusBonus = b.comment("強化護甲修復包：每次增加的耐久上限（0.5 = +50% 原始值，可疊加）").defineInRange("armor_plus_bonus", 0.5, 0.0, 10.0);
        armorPlusMaxStacks = b.comment("強化護甲修復包：最多疊加幾次（0 = 不設上限）").defineInRange("armor_plus_max_stacks", 0, 0, 1000);
        shelterSeconds = b.comment("圈外庇護：持續秒數").defineInRange("shelter_seconds", 15.0, 1.0, 600.0);
        shelterBonus = b.comment("圈外庇護：在圈外時的移動速度加成（0.25 = +25%）").defineInRange("shelter_bonus", 0.25, 0.0, 5.0);
        radarRange = b.comment("雷達脈衝：掃描半徑（格，含高低差）").defineInRange("radar_range", 40.0, 1.0, 500.0);
        radarSeconds = b.comment("雷達脈衝：發光與地圖標點秒數").defineInRange("radar_seconds", 3.0, 0.5, 60.0);
        decoySeconds = b.comment("殘影分身：存在秒數").defineInRange("decoy_seconds", 10.0, 1.0, 120.0);
        decoyRevealSeconds = b.comment("殘影分身：打掉分身的人發光幾秒（所有人都看得到，不上地圖；0 = 不發光）").defineInRange("decoy_reveal_seconds", 3.0, 0.0, 60.0);
        b.pop();
        b.pop();
    }

    public static SpecialConfig get() { return PGConfig.SERVER.special; }

    /** 特殊物品模式是否開啟（設定還沒載入時當作開啟）。 */
    public static boolean enabledSafe() {
        try { return get().enabled.get(); } catch (IllegalStateException e) { return true; }
    }

    /** 切換特殊物品模式（寫進設定檔），並更新所有人的大廳資料（客戶端用來決定要不要顯示特殊物品欄）。 */
    public static void setEnabled(boolean on) {
        get().enabled.set(on);
        get().enabled.save();
        com.provinggrounds.core.LobbyManager.get().broadcast();
    }

    public static int ticks(ForgeConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20);
    }
}
