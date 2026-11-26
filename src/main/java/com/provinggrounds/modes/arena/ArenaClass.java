package com.provinggrounds.modes.arena;

import java.util.List;
import java.util.Set;

/**
 * 檔案: ArenaClass.java
 * 目的: 團隊死鬥／佔點的九大職業（計畫書 9 重製）。
 *   - 每種職業有限定槍枝分類，打開商店只看到自己能買的槍（自由人無限制、商店八折）。
 *   - 取消局內更換職業；開局 10 秒準備期間自動跳出選角介面（LoadoutScreen）選擇。
 */
public enum ArenaClass {
    FREELANCER("自由人", 100, "tacz:glock_17", "SEMI",
        "商店八折，沒有限定槍械，什麼都能買",
        Set.of("手槍", "衝鋒槍", "散彈槍", "步槍", "機槍", "狙擊槍", "重武器"),
        List.of(), 0.0, 0.0, 0),

    ASSAULT("突擊兵", 90, "tacz:glock_17", "SEMI",
        "移動速度 +20%，擊殺回復 20 HP",
        Set.of("手槍", "步槍"),
        List.of(), 0.20, 0.0, 0),

    HEAVY("重裝兵", 110, "tacz:p320", "SEMI",
        "正面受到的傷害 -10%，移動速度 -15%",
        Set.of("手槍", "機槍", "散彈槍"),
        List.of(), -0.15, 0.0, 0),

    SNIPER("狙擊手", 80, "tacz:p320", "SEMI",
        "用狙擊槍打中的敵人發光 0.5 秒（隊友可見）",
        Set.of("手槍", "狙擊槍"),
        List.of(), 0.0, 0.0, 0),

    ENGINEER("工兵", 100, "tacz:p320", "SEMI",
        "自帶手榴彈＋瞬爆手榴彈（擊殺刷新）；爆炸傷害 +20%，受炸傷害 -50%",
        Set.of("手槍", "重武器", "散彈槍"),
        List.of(Kits.throwable("m67"), Kits.throwable("rgn")), 0.0, 0.0, 0),

    RECON("偵察兵", 100, "tacz:glock_17", "SEMI",
        "移動速度 +10%，每 20 秒讓最近敵人發光 1 秒",
        Set.of("手槍", "衝鋒槍", "步槍"),
        List.of(), 0.10, 0.0, 0),

    SUPPORT("支援兵", 100, "tacz:glock_17", "SEMI",
        "身邊 4 格內隊友（含自己）每 30 秒補一顆隨機投擲物",
        Set.of("手槍", "機槍", "步槍"),
        List.of(), 0.0, 0.0, 0),

    ASSASSIN("刺客", 70, "tacz:b93r", "AUTO",
        "拿刀速度 +30%，背刺 ×3，走路聲音 -50%",
        Set.of("手槍", "衝鋒槍"),
        List.of(Kits.DAGGER), 0.0, 0.30, 0),

    MEDIC("醫護兵", 100, "tacz:glock_17", "SEMI",
        "身邊 4 格內隊友每秒回 1 HP；扶起隊友只需 2 秒",
        Set.of("手槍", "衝鋒槍", "散彈槍"),
        List.of(), 0.0, 0.0, 40);

    public final String displayName;
    public final int maxHealth;
    public final String gun, fireMode, description;
    /** 這個職業在商店能買的槍械分類。 */
    public final Set<String> allowedGunCats;
    /** 每次開局／重生（工兵擊殺時）補到 1 個的東西（格式同 /give）。 */
    public final List<String> consumables;
    /** 常駐移動速度加成（+0.20 = +20%，-0.15 = -15%）。 */
    public final double speedBonus;
    /** 拿刀時額外移動速度加成（刺客 +0.30）。 */
    public final double meleeSpeedBonus;
    /** 扶起隊友所需的 tick（0 = 用一般設定 5.5 秒；醫護兵 = 40 tick = 2 秒）。 */
    private final int fastReviveTicks;

    ArenaClass(String displayName, int maxHealth, String gun, String fireMode, String description,
               Set<String> allowedGunCats, List<String> consumables,
               double speedBonus, double meleeSpeedBonus, int fastReviveTicks) {
        this.displayName = displayName;
        this.maxHealth = maxHealth;
        this.gun = gun;
        this.fireMode = fireMode;
        this.description = description;
        this.allowedGunCats = allowedGunCats;
        this.consumables = consumables;
        this.speedBonus = speedBonus;
        this.meleeSpeedBonus = meleeSpeedBonus;
        this.fastReviveTicks = fastReviveTicks;
    }

    public int fastReviveTicks() { return fastReviveTicks; }

    /** 這個職業能不能買商店裡某個分類的槍械（非槍械分類一律可以）。 */
    public boolean canBuyCategory(String cat) {
        if (!ALL_GUN_CATS.contains(cat)) return true;
        return allowedGunCats.contains(cat);
    }

    /** 這個職業能不能使用／拾取這把槍（依 TACZ 槍械類型轉換成職業分類）。 */
    public boolean canUseGunType(@javax.annotation.Nullable String gunType) {
        if (this == FREELANCER || gunType == null || gunType.isEmpty()) return true;
        String cat = switch (gunType.toLowerCase(java.util.Locale.ROOT)) {
            case "pistol" -> "手槍";
            case "smg" -> "衝鋒槍";
            case "shotgun" -> "散彈槍";
            case "rifle" -> "步槍";
            case "mg" -> "機槍";
            case "sniper" -> "狙擊槍";
            case "rpg" -> "重武器";
            default -> "其他槍械";
        };
        return canBuyCategory(cat);
    }

    /** 簡短顯示限定槍械文字（選角畫面用）。 */
    public String gunSummary() {
        if (this == FREELANCER) return "全部槍械（商店八折）";
        return String.join("、", allowedGunCats.stream().sorted((a, b) -> Integer.compare(GUN_ORDER.indexOf(a), GUN_ORDER.indexOf(b))).toList());
    }

    public static final List<String> GUN_ORDER = List.of("步槍", "衝鋒槍", "散彈槍", "機槍", "狙擊槍", "重武器", "手槍");
    public static final Set<String> ALL_GUN_CATS = Set.of("手槍", "衝鋒槍", "散彈槍", "步槍", "機槍", "狙擊槍", "重武器", "其他槍械");

    public static ArenaClass byOrdinal(int i) {
        ArenaClass[] v = values();
        return i >= 0 && i < v.length ? v[i] : FREELANCER;
    }

    /** 補品、投擲物與近戰（NBT）。 */
    public static final class Kits {
        private Kits() {}

        /** 團隊模式補品（計畫書 10）：兩個 AI-2 急救包。 */
        public static final String AI2_MEDKIT = "lrtactical:consumable{ConsumableId:\"lrtactical:ai2\"}";
        public static final int TEAM_HEAL_COUNT = 2;
        public static final String DAGGER = "lrtactical:melee{MeleeWeaponId:\"lrtactical:dagger\"}";

        public static final List<String> SUPPORT_THROWABLES = List.of("m67", "rgn", "molotov", "flash_grenade", "smoke_grenade");

        public static String throwable(String id) {
            return "lrtactical:throwable{ThrowableId:\"lrtactical:" + id + "\"}";
        }
    }
}
