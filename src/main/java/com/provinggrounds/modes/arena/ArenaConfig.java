package com.provinggrounds.modes.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: ArenaConfig.java
 * 目的: 團隊死鬥、佔點、槍王之王的伺服器設定（server toml 的 [arena]）。
 *   預設值全部照舊版資料包（座標、金錢、分數）。座標用「x y z」字串，方便管理員直接改。
 */
public final class ArenaConfig {

    // 共用
    public final ForgeConfigSpec.ConfigValue<String> redSpawn, blueSpawn;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> barriers, supplyChests;
    public final ForgeConfigSpec.BooleanValue downedDefault;
    public final ForgeConfigSpec.DoubleValue arenaReviveSeconds;
    public final ForgeConfigSpec.IntValue baseRadius, startMoney, transferCooldown, endSeconds;
    public final ForgeConfigSpec.ConfigValue<String> ammoBox;
    public final ForgeConfigSpec.BooleanValue antiCamp, antiCampNoFire;
    // 計畫書 5
    public final ForgeConfigSpec.IntValue respawnDelay, disconnectTimeout, spawnProtect, antiCampRadius, antiCampWarn,
        antiCampStart, antiCampStep, antiCampMax;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> redSpawnsExtra, blueSpawnsExtra;

    // 團隊死鬥
    public final ForgeConfigSpec.IntValue tdmKills, tdmMinutes, tdmKillMoney, tdmDeathMoney, tdmLoserBonus;
    public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> tdmStageMultiplier, tdmStageBonus;

    // 佔點
    public final ForgeConfigSpec.IntValue domScore, domRoundScore, domSurviveScore, domRoundSeconds, domFreezeSeconds,
        domSecondPointAt, domCaptureSeconds, domResultSeconds, domFirstPrepSeconds, domPrepSeconds, domMoneyCap,
        domKillMoney, domFirstBloodMoney, domPointIncome, domTieMoney, domTieAlivePenalty, domTieDeadPay;
    public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> domWinMoney, domLossMoney;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> domPoints;
    // 佔點特殊物品裝備（計畫書 8）
    public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> domGearMarkup;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> domGearTiers;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> domGearItemMarkups;

    // 槍王之王
    public final ForgeConfigSpec.IntValue gkMultiplier;
    public final ForgeConfigSpec.ConfigValue<String> gkMedkit;

    public ArenaConfig(ForgeConfigSpec.Builder b) {
        b.comment("團隊死鬥、佔點、槍王之王（預設值照舊版資料包）").push("arena");
        downedDefault = b.comment("團隊模式（團隊死鬥、佔點、槍王之王）的倒地預設值（大廳可以切換；計畫書 7 E）").define("downed_enabled_v1", false);
        arenaReviveSeconds = b.comment("團隊模式開啟倒地時，一般職業扶起隊友需要的秒數（醫護兵固定 2 秒；沒有復活牌）").defineInRange("revive_seconds_v1", 5.5, 0.5, 60.0);
        redSpawn = b.comment("紅隊重生點（x y z）").define("red_spawn", "201.18 14 -87.2");
        blueSpawn = b.comment("藍隊重生點（x y z）").define("blue_spawn", "121.57 14 -7.56");
        barriers = b.comment("基地出口的屏障（x1 y1 z1 x2 y2 z2）。佔點準備階段封住，戰鬥開始拆掉；團隊死鬥開局拆掉")
            .defineListAllowEmpty(List.of("barriers"), () -> List.of(
                "191 14 -90 191 18 -86", "199 14 -78 203 18 -78", "192 13 -79 195 13 -82",
                "131 14 -6 131 18 -10", "123 14 -18 119 18 -18", "127 13 -14 130 13 -17"), ArenaConfig::isString);
        supplyChests = b.comment("基地補給箱（x y z），每回合清空，避免把東西藏在裡面")
            .defineListAllowEmpty(List.of("supply_chests"), () -> List.of("193 14 -84", "129 14 -12", "193 14 -85", "129 14 -11"), ArenaConfig::isString);
        baseRadius = b.comment("基地範圍：重生點周圍幾格（水平）。在基地裡才能買東西、換職業").defineInRange("base_radius", 8, 1, 64);
        startMoney = b.comment("開局金錢").defineInRange("start_money", 5, 0, 1_000_000);
        transferCooldown = b.comment("轉帳冷卻（秒；0 = 沒有冷卻）").defineInRange("transfer_cooldown_v2", 0, 0, 3600); // 計畫書 5 改名：舊的 45 秒不會蓋掉新預設
        endSeconds = b.comment("比賽結束後幾秒回大廳").defineInRange("end_seconds", 10, 1, 120);
        ammoBox = b.comment("無限彈藥箱（格式同 /give）").define("ammo_box", "tacz:ammo_box{AllTypeCreative:1b}");
        antiCamp = b.comment("防蹲點：進入敵方重生點附近（水平距離 anti_camp_radius 格內）先警告，之後每秒受傷、緩速、發光").define("anti_camp", true);
        antiCampRadius = b.comment("防蹲點範圍（格，水平距離）。只在團隊死鬥有效。原本的鍵 anti_camp_radius（18）已停用，改名後預設 25（計畫書 6 A13）").defineInRange("anti_camp_radius_v2", 25, 2, 128);
        antiCampWarn = b.comment("進入後先警告幾秒才開始受傷").defineInRange("anti_camp_warn", 2, 0, 60);
        antiCampStart = b.comment("第一秒的傷害").defineInRange("anti_camp_start", 2, 0, 1000);
        antiCampStep = b.comment("之後每秒多扣多少（線性遞增）").defineInRange("anti_camp_step", 5, 0, 1000);
        antiCampMax = b.comment("每秒傷害上限").defineInRange("anti_camp_max", 100, 1, 1000);
        antiCampNoFire = b.comment("在敵方重生區內不能開槍").define("anti_camp_no_fire", false);
        spawnProtect = b.comment("重生保護秒數（無敵但不能攻擊；開槍或時間到就結束；0 = 關閉）").defineInRange("spawn_protect", 5, 0, 60);
        redSpawnsExtra = b.comment("紅隊額外的重生點（x y z），重生時從 red_spawn 和這些點隨機選一個").defineListAllowEmpty(List.of("red_spawns_extra"), List::of, ArenaConfig::isString);
        blueSpawnsExtra = b.comment("藍隊額外的重生點（x y z）").defineListAllowEmpty(List.of("blue_spawns_extra"), List::of, ArenaConfig::isString);
        respawnDelay = b.comment("團隊死鬥、槍王之王：死亡後幾秒才重生（倒數期間是旁觀）").defineInRange("respawn_delay", 3, 0, 60);
        disconnectTimeout = b.comment("掉線超過幾秒就移出比賽與隊伍（所有模式；移出前都算隊伍人數）").defineInRange("disconnect_timeout", 300, 10, 86400);


        b.comment("團隊死鬥").push("tdm");
        tdmKills = b.comment("預設目標擊殺數（大廳可以調）").defineInRange("target_kills", 100, 1, 100_000);
        tdmMinutes = b.comment("預設時間上限（分鐘，大廳可以調）").defineInRange("minutes", 10, 1, 600);
        tdmKillMoney = b.comment("擊殺獎金（早期，之後乘上階段倍率）").defineInRange("kill_money", 20, 0, 100_000);
        tdmDeathMoney = b.comment("陣亡補貼（早期）").defineInRange("death_money", 15, 0, 100_000);
        tdmLoserBonus = b.comment("落後隊伍的額外補貼（陣亡、擊殺各加一次；早期）").defineInRange("loser_bonus", 5, 0, 100_000);
        tdmStageMultiplier = b.comment("早／中／後期的金錢倍率（時間的 1/3、2/3 換階段）")
            .defineList("stage_multiplier", List.of(1, 3, 9), ArenaConfig::isInt);
        tdmStageBonus = b.comment("進入中期、後期時落後隊伍每人的獎金")
            .defineList("stage_bonus", List.of(750, 1500), ArenaConfig::isInt);
        b.pop();

        b.comment("佔點").push("dom");
        domScore = b.comment("預設目標分數（大廳可以調）").defineInRange("target_score", 1000, 100, 100_000);
        domRoundScore = b.comment("贏一回合加幾分").defineInRange("round_score", 300, 0, 100_000);
        domSurviveScore = b.comment("平手回合：存活人數較多的隊伍加幾分").defineInRange("survive_score", 50, 0, 100_000);
        domRoundSeconds = b.comment("一回合最長幾秒").defineInRange("round_seconds", 120, 10, 3600);
        domFreezeSeconds = b.comment("回合開始後幾秒內據點不能佔").defineInRange("freeze_seconds", 30, 0, 600);
        domSecondPointAt = b.comment("回合第幾秒可能開第二個據點（50% 機率）").defineInRange("second_point_at", 90, 1, 3600);
        domCaptureSeconds = b.comment("佔領需要幾秒（進度上限）").defineInRange("capture_seconds", 13, 1, 600);
        domResultSeconds = b.comment("回合結算幾秒").defineInRange("result_seconds", 10, 1, 120);
        domFirstPrepSeconds = b.comment("第一回合準備幾秒").defineInRange("first_prep_seconds", 10, 1, 600);
        domPrepSeconds = b.comment("之後每回合準備幾秒（可以買東西）").defineInRange("prep_seconds", 25, 1, 600);
        domMoneyCap = b.comment("金錢上限").defineInRange("money_cap", 2500, 0, 10_000_000);
        domKillMoney = b.comment("擊殺獎金").defineInRange("kill_money", 12, 0, 100_000);
        domFirstBloodMoney = b.comment("每回合首殺獎金").defineInRange("first_blood_money", 160, 0, 100_000);
        domPointIncome = b.comment("站在開放據點上每秒得到的錢").defineInRange("point_income", 2, 0, 100_000);
        domWinMoney = b.comment("回合勝利獎金（依連勝次數，最後一個之後都一樣）")
            .defineList("win_money", List.of(560, 480, 400, 320, 240), ArenaConfig::isInt);
        domLossMoney = b.comment("回合失敗獎金（依連敗次數）")
            .defineList("loss_money", List.of(360, 520, 680, 840, 1000), ArenaConfig::isInt);
        domTieMoney = b.comment("平手回合每人獎金").defineInRange("tie_money", 400, 0, 100_000);
        domTieAlivePenalty = b.comment("平手回合：活著的人扣錢").defineInRange("tie_alive_penalty", 320, 0, 100_000);
        domTieDeadPay = b.comment("平手回合：陣亡的人補償").defineInRange("tie_dead_pay", 160, 0, 100_000);
        domPoints = b.comment("據點：名稱|中心 x y z|半徑|地板 x1 y1 z1 x2 y2 z2（開放時地板變金磚、關閉時鐵磚）")
            .defineListAllowEmpty(List.of("points"), () -> List.of(
                "A|192.5 24 -16.5|4|196 24 -13 188 24 -21",
                "B|161.5 13 -47.5|5|166 13 -53 156 13 -43",
                "C|122.5 13 -86.5|8|130 13 -79 115 13 -94"), ArenaConfig::isString);
        b.comment("佔點的特殊道具（大廳特殊道具模式開著時）：整場選一種、依強度冷卻（弱 1／中 2／強 3 回合），每種道具各有獨立商店加價").push("gear");
        domGearMarkup = b.comment("各強度的備用預設加價 %（弱、中、強；僅在該道具沒有單獨設定加價時使用）").defineList("markup_v2", List.of(5, 12, 22), ArenaConfig::isInt);
        domGearTiers = b.comment("強度覆寫：「物品ID=等級」（1 弱、2 中、3 強、0 團隊模式不能用）。沒寫的用模組內建的評估")
            .defineListAllowEmpty(List.of("tier_overrides_v1"), List::of, ArenaConfig::isString);
        domGearItemMarkups = b.comment("單獨道具商店加價覆寫：「物品ID=加價百分比」（例：totem=25、smoke_burst=5）。沒寫的用模組內建各道具獨立加價")
            .defineListAllowEmpty(List.of("item_markup_overrides_v1"), List::of, ArenaConfig::isString);
        b.pop();
        b.pop();

        b.comment("槍王之王（重生點與武器清單在模組內的 data/provinggrounds/arena/gk.json）").push("gk");
        gkMultiplier = b.comment("預設擊殺倍率：每階段需要「隊伍人數 × 倍率」次擊殺（大廳可以調）").defineInRange("multiplier", 1, 1, 20);
        gkMedkit = b.comment("每人常駐的醫療包（格式同 /give，空字串＝不給）").define("medkit", "lrtactical:consumable{ConsumableId:\"lrtactical:carfak\"}");
        b.pop();

        b.pop();
    }

    private static boolean isString(Object o) { return o instanceof String; }
    private static boolean isInt(Object o) { return o instanceof Integer; }

    // ── 解析工具 ──────────────────────────────────────────────────────────────

    @Nullable
    public static Vec3 vec(String s) {
        double[] d = nums(s, 3);
        return d == null ? null : new Vec3(d[0], d[1], d[2]);
    }

    /** 「x1 y1 z1 x2 y2 z2」→ 兩個角。 */
    @Nullable
    public static BlockPos[] box(String s) {
        double[] d = nums(s, 6);
        if (d == null) return null;
        return new BlockPos[] { BlockPos.containing(d[0], d[1], d[2]), BlockPos.containing(d[3], d[4], d[5]) };
    }

    @Nullable
    static double[] nums(String s, int n) {
        String[] p = s.trim().split("\\s+");
        if (p.length < n) return null;
        double[] out = new double[n];
        try {
            for (int i = 0; i < n; i++) out[i] = Double.parseDouble(p[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    /** 清單的第 i 個（超過就用最後一個）。 */
    public static int at(List<? extends Integer> list, int i) {
        if (list.isEmpty()) return 0;
        return list.get(Math.max(0, Math.min(i, list.size() - 1)));
    }

    public static List<BlockPos[]> boxes(List<? extends String> list) {
        List<BlockPos[]> out = new ArrayList<>();
        for (String s : list) {
            BlockPos[] b = box(s);
            if (b != null) out.add(b);
        }
        return out;
    }
}
