package com.provinggrounds.modes.arena;

import java.util.List;

/**
 * 檔案: ArenaClass.java
 * 目的: 團隊死鬥／佔點的職業（照舊版 class/*.mcfunction）。
 *   每次重生補滿「固有消耗品」（補品、投擲物，每樣 1 個）；起始槍只在開局、換職業時給。
 *   自由人買東西打八折（商店的 free 價格）。
 */
public enum ArenaClass {
    FREELANCER("自由人", 80, "tacz:cz75", "AUTO", "買東西打八折、血量 80",
        List.of(Kits.BIG_HEAL)),
    ASSAULT("突擊兵", 100, "suffuse:tt33", "SEMI", "常駐速度 II",
        List.of(Kits.BIG_HEAL, Kits.throwable("rgn"), Kits.throwable("flash_grenade"))),
    SUPPORT("支援兵", 100, "suffuse:mas38", "AUTO", "速補、AI-2 和五種投擲物",
        List.of(Kits.FAST_HEAL, Kits.AI2, Kits.throwable("molotov"), Kits.throwable("m67"),
            Kits.throwable("rgn"), Kits.throwable("smoke_grenade"), Kits.throwable("flash_grenade"))),
    SENTINEL("哨兵", 100, "suffuse:m1895", "SEMI", "蹲著不動：抗性 I、每秒 +$1",
        List.of(Kits.BIG_HEAL, Kits.throwable("m67"), Kits.throwable("smoke_grenade")));

    public final String displayName;
    public final int maxHealth;
    public final String gun, fireMode, description;
    /** 每次重生補到 1 個的東西（格式同 /give）。 */
    public final List<String> consumables;

    ArenaClass(String displayName, int maxHealth, String gun, String fireMode, String description, List<String> consumables) {
        this.displayName = displayName;
        this.maxHealth = maxHealth;
        this.gun = gun;
        this.fireMode = fireMode;
        this.description = description;
        this.consumables = consumables;
    }

    public static ArenaClass byOrdinal(int i) {
        ArenaClass[] v = values();
        return i >= 0 && i < v.length ? v[i] : FREELANCER;
    }

    /** 舊版資料包的補品與投擲物（NBT 原封不動）。 */
    public static final class Kits {
        private Kits() {}

        public static final String BIG_HEAL = "minecraft:potion{display:{Name:'[{\"text\":\"\",\"italic\":false},{\"text\":\"大補\",\"color\":\"red\"}]',"
            + "Lore:['[{\"text\":\"\",\"italic\":false},{\"text\":\"一次補滿的補包 (這會讓你發光一秒)\",\"color\":\"red\"}]']},"
            + "CustomPotionEffects:[{Id:6b,Amplifier:120b,Duration:20,ShowParticles:0b,ShowIcon:0b},{Id:24b,Amplifier:0b,Duration:20,ShowParticles:0b,ShowIcon:0b}],"
            + "CustomPotionColor:16711680,PgArenaKit:1b}";
        public static final String FAST_HEAL = "minecraft:splash_potion{display:{Name:'[{\"text\":\"\",\"italic\":false},{\"text\":\"速補\",\"color\":\"red\"}]',"
            + "Lore:['[{\"text\":\"\",\"italic\":false},{\"text\":\"強化後的大補 可以快速補血 並給你額外生命值\",\"color\":\"red\"}]']},"
            + "CustomPotionEffects:[{Id:6b,Amplifier:118b,Duration:20,ShowParticles:0b,ShowIcon:0b},{Id:12b,Amplifier:0b,Duration:20,ShowParticles:0b,ShowIcon:0b},"
            + "{Id:22b,Amplifier:1b,Duration:300,ShowParticles:0b,ShowIcon:0b}],CustomPotionColor:16711680,PgArenaKit:1b}";
        public static final String AI2 = "lrtactical:consumable{ConsumableId:\"lrtactical:ai2\"}";

        public static String throwable(String id) {
            return "lrtactical:throwable{ThrowableId:\"lrtactical:" + id + "\"}";
        }
    }
}
