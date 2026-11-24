package com.provinggrounds.special;

/**
 * 檔案: SpecialType.java
 * 目的: 全部特殊物品的清單（計畫書 4 的 D 章，1～34 號）。
 *   每一種：物品 ID（provinggrounds:<id>）、繁體中文名稱、英文名稱、說明、占位貼圖的主色。
 *   箱子出現的權重寫在資料檔 data/br/loot_tables/special.json（改權重不用改程式）。
 *   效果寫在 SpecialUse（物品只是參數，邏輯在共用引擎）。
 */
public enum SpecialType {
    BLIND_ORB("blind_orb", "致盲珠", "Blinding Orb", "往前方推出一團 3×3 黑霧（可穿牆），碰到的人致盲 5 秒", 0x202020),
    ADRENALINE("adrenaline", "腎上腺素", "Adrenaline", "15 秒內移動速度 +75%（不和拿刀的速度疊加）", 0xE04040),
    INVIS_CLOAK("invis_cloak", "隱形斗篷", "Invisibility Cloak", "5 秒隱身、移動速度 +25%；期間不能開箱、不能使用任何物品", 0x8080C0),
    SPRING_SHOES("spring_shoes", "彈簧鞋", "Spring Shoes", "朝面向方向衝刺（任何方向，包含往上）", 0x40C040),
    ENEMY_SCANNER_BASIC("enemy_scanner_basic", "簡略的敵人偵測器", "Basic Enemy Scanner", "隨機一名敵人發光並顯示在地圖上（只有你看得到）", 0x40A0E0),
    ENEMY_SCANNER("enemy_scanner", "敵人偵測器", "Enemy Scanner", "隨機一名敵人發光並顯示在地圖上（只有你看得到）", 0x2080E0),
    ENEMY_SCANNER_PLUS("enemy_scanner_plus", "敵人偵測器加強版", "Enemy Scanner+", "其中一隊的所有敵人發光並顯示在地圖上（你的整隊看得到）", 0x1050C0),
    SNOW_WALL("snow_wall", "臨時雪牆", "Snow Wall", "發射雪球，飛過的地面升起最高 4 格的雪牆，15 秒後消失", 0xF0F8FF),
    KNOCKBACK_SHIELD("knockback_shield", "擊退盾", "Knockback Shield", "第一次使用：舉起擊退盾；再次使用：向前衝撞，撞到人或牆就釋放衝擊波", 0x806040),
    WIND_BOMB("wind_bomb", "擊退風暴彈", "Wind Bomb", "投出風彈，落地處產生衝擊波", 0xA0E0E0),
    FAKE_CRATE_LOW("fake_crate_low", "假的普通箱", "Fake Common Crate", "在前方放一個假的普通箱；打開的人會受傷、發光、震盪", 0xA07040),
    FAKE_CRATE_MID("fake_crate_mid", "假的中階箱", "Fake Mid Crate", "在前方放一個假的中階箱；打開的人會受傷、發光、震盪", 0x8090A0),
    FAKE_CRATE_HIGH("fake_crate_high", "假的高階箱", "Fake High Crate", "在前方放一個假的高階箱；打開的人會受傷、發光、震盪", 0xE0B030),
    RECON_MINE("recon_mine", "偵查地雷", "Recon Mine", "敵人靠近時，讓附近的敵人發光並顯示在地圖上（你看得到）", 0x3070F0),
    LAND_MINE("land_mine", "地雷", "Land Mine", "敵人靠近時爆炸，附近所有人受傷並緩速", 0x704020),
    FIRE_MINE("fire_mine", "燃燒地雷", "Fire Mine", "敵人靠近時，附近所有人燃燒 5 秒", 0xF06020),
    SHOCK_MINE("shock_mine", "震爆地雷", "Shock Mine", "敵人靠近時產生衝擊波", 0xF0F0F0),
    FLARE_GUN("flare_gun", "信號槍", "Flare Gun", "在你的位置召喚一個空投（會通知全體玩家）", 0xF04040),
    BLAST_SCANNER_BASIC("blast_scanner_basic", "簡略的爆破敵人偵測器", "Basic Blast Scanner", "隨機一名敵人發光（所有人都看得到，不上地圖）", 0xE0A040),
    BLAST_SCANNER("blast_scanner", "爆破敵人偵測器", "Blast Scanner", "隨機一名敵人發光並顯示在地圖上（所有人都看得到）", 0xE08020),
    BLAST_SCANNER_PLUS("blast_scanner_plus", "爆破敵人偵測器加強版", "Blast Scanner+", "其中一隊的所有敵人發光並顯示在地圖上（所有人都看得到）", 0xC05010),
    SMOKE_BURST("smoke_burst", "煙幕彈", "Smoke Burst", "以自己為中心放出半徑 8 格的平放圓柱形煙霧", 0x909090),
    TOTEM("totem", "不死圖騰", "Totem", "15 秒內副手有不死圖騰：受到致命傷時不死也不倒地，回到 50 血、額外生命 +25、恢復效果 10 秒", 0xF0D040),
    SHIELD_BUBBLE("shield_bubble", "護盾泡泡", "Shield Bubble", "15 秒內第一次受到傷害時完全擋下，並獲得 25 額外生命值與 10 秒加速", 0x60C0FF),
    WARP_PEARL("warp_pearl", "傳送珠", "Warp Pearl", "丟出不受重力的珍珠，再按一次使用鍵或飛到盡頭時，傳送到它正下方", 0x20A080),
    SILENT_BOOTS("silent_boots", "靜音靴", "Silent Boots", "15 秒內移動不會發出聲音", 0x404060),
    GAS_GRENADE("gas_grenade", "毒氣彈", "Gas Grenade", "投出毒氣彈，範圍內的敵人視野模糊、咳嗽、失去方向感、中毒", 0x60C020),
    FIRST_AID_SHOT("first_aid_shot", "急救針", "First Aid Shot", "只有在扶起隊友時與自身倒地狀態時才能使用（扶隊友：立刻扶起並回滿血｜倒地：10 秒後自救）", 0xF0F0F0),
    ARMOR_REPAIR("armor_repair", "護甲修復包", "Armor Repair Kit", "身上所有護甲的耐久度修到滿", 0x6080A0),
    ARMOR_REPAIR_PLUS("armor_repair_plus", "強化護甲修復包", "Armor Repair Kit+", "修滿所有護甲，並讓每件護甲的耐久上限 +50%（可疊加）", 0x4060C0),
    ZONE_SHELTER("zone_shelter", "圈外庇護", "Zone Shelter", "15 秒內不受圈外傷害，在圈外時移動速度 +25%", 0xC0F0C0),
    RADAR_PULSE("radar_pulse", "雷達脈衝", "Radar Pulse", "掃描 40 格內的敵人，讓他們發光並顯示在地圖上 3 秒（只有你看得到）", 0x40F0A0),
    FAKE_GUNSHOT("fake_gunshot", "假槍聲", "Fake Gunshot", "在準星指的位置連續發出 6 秒槍聲（手上那把槍的聲音，要拿著槍才能用）", 0x606060),
    DECOY("decoy", "殘影分身", "Decoy", "產生一個跟你一模一樣的分身，往前奔跑 10 秒", 0xA0A0F0),
    // ── 計畫書 6 E 章 ──
    BRAINWASH("brainwash", "不可控的洗腦工具", "Uncontrollable Brainwasher", "自身半徑 25 格內所有玩家（包括自己）15 秒內不能開物品欄、不能按 F 互動、不能切換快捷欄", 0xC040C0),
    ROOT_ORB("root_orb", "定身球", "Root Orb", "投出，落地後半徑 2.5 格內所有玩家（包括自己）3 秒不能移動（可以轉視角）", 0x50E0B0),
    FIRE_DRILL("fire_drill", "火焰鑽頭", "Fire Drill", "投出、不受重力，10 秒沒碰到方塊就消失；鑽進方塊 7 秒，方塊後方扇形範圍內所有玩家每秒灼燒 5 點（持續 3 秒）", 0xF07020),
    RECALL("recall", "回溯點", "Recall Point", "第一次使用：記錄位置（不消耗）；60 秒內再用一次回到記錄的位置。60 秒沒用自動消失", 0x40C0F0),
    GAMBLER_ROD("gambler_rod", "賭徒釣竿", "Gambler's Rod", "各 25%：掉 1 個特殊物品＋最大生命 +5｜掉 2 個＋最大生命 +10｜受 25 傷害、最大生命 -5、震盪 5 秒｜受 75 傷害、最大生命 -10、震盪 5 秒", 0xD0A040),
    RECALL_PLUS("recall_plus", "回溯點加強版", "Recall Point+", "同回溯點，另外記錄血量：回去時血量恢復成記錄時的值", 0x2080F0),
    GAMBLER_ROD_PLUS("gambler_rod_plus", "沒救的賭徒釣竿", "Hopeless Gambler's Rod", "各 25%：掉 2 個特殊物品＋最大生命 +10｜掉 4 個＋最大生命 +20｜受 50 傷害、最大生命 -10、震盪 5 秒｜受 100 傷害、最大生命 -20、震盪 5 秒", 0xB02020),
    THOR_HAMMER("thor_hammer", "雷神錘", "Thor's Hammer", "準心指的地點水平半徑 5 格內隨機落雷，每 0.2 秒一道，持續 5 秒（包括自己）", 0x80A0FF),
    THOR_HAMMER_PLUS("thor_hammer_plus", "雷神錘加強版", "Thor's Hammer+", "準心指的地點水平半徑 5 格內的所有玩家，每 0.5 秒被雷打中一次，持續 5 秒（包括自己）", 0x4060FF),
    RESKYDIVE("reskydive", "重新跳傘器", "Re-Skydiver", "（大逃殺）把自己傳送到目前位置上方 300 格，重新跳傘", 0x60B0E0),
    RESKYDIVE_GROUP("reskydive_group", "多人重新跳傘器", "Group Re-Skydiver", "（大逃殺）自身半徑 3 格內所有玩家（包括自己）傳送到你上方 300 格，重新跳傘", 0x3090C0),
    RESKYDIVE_TARGET("reskydive_target", "多人重新跳傘指定器", "Targeted Re-Skydiver", "（大逃殺）準心指的地點水平半徑 3 格內所有玩家傳送到你上方 300 格，重新跳傘", 0x2070A0);

    public final String id, zh, en, desc;
    public final int color;

    SpecialType(String id, String zh, String en, String desc, int color) {
        this.id = id;
        this.zh = zh;
        this.en = en;
        this.desc = desc;
        this.color = color;
    }

    public static SpecialType byId(String id) {
        for (SpecialType t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    public boolean isFakeCrate() { return this == FAKE_CRATE_LOW || this == FAKE_CRATE_MID || this == FAKE_CRATE_HIGH; }

    public boolean isMine() { return this == RECON_MINE || this == LAND_MINE || this == FIRE_MINE || this == SHOCK_MINE; }
}
