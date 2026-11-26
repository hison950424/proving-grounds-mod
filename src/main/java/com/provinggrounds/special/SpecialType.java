package com.provinggrounds.special;

/**
 * 檔案: SpecialType.java
 * 目的: 全部特殊道具的清單（計畫書 4、6、9，共 62 種）。
 *   每一種：物品 ID（provinggrounds:<id>）、繁體中文名稱、英文名稱、說明、占位貼圖的主色。
 *   箱子出現的權重寫在資料檔 data/br/loot_tables/special.json（改權重不用改程式）。
 */
public enum SpecialType {
    BLIND_ORB("blind_orb", "致盲珠", "Blinding Orb", "往前方推出一團 3×3 黑霧（可穿牆），碰到的人致盲 5 秒", 0x202020),
    ADRENALINE("adrenaline", "強心劑", "Stimulant", "使用時自己受到 10 傷害（不會致死）；15 秒內移動速度 +50%（不和拿刀的速度疊加）；期間倒地的話，倒地後前 5 秒可以使用任何物品", 0xE04040),
    INVIS_CLOAK("invis_cloak", "隱形斗篷", "Invisibility Cloak", "5 秒隱身、移動速度 +25%；期間不能開箱、不能使用任何物品", 0x8080C0),
    SPRING_SHOES("spring_shoes", "彈簧鞋", "Spring Shoes", "朝面向方向衝刺（任何方向，包含往上）", 0x40C040),
    ENEMY_SCANNER_BASIC("enemy_scanner_basic", "簡略的敵人偵測器", "Basic Enemy Scanner", "隨機一名敵人發光並顯示在地圖上（只有你看得到）", 0x40A0E0),
    ENEMY_SCANNER("enemy_scanner", "敵人偵測器", "Enemy Scanner", "隨機一名敵人發光並顯示在地圖上（只有你看得到）", 0x2080E0),
    ENEMY_SCANNER_PLUS("enemy_scanner_plus", "敵人偵測器加強版", "Enemy Scanner+", "其中一隊的所有敵人發光並顯示在地圖上（你的整隊看得到）", 0x1050C0),
    SNOW_WALL("snow_wall", "臨時雪牆", "Snow Wall", "發射雪球（可反彈牆壁 1 次，落地馬上觸發），飛過的地面升起最高 4 格的雪牆，15 秒後消失（可以用 2 次）", 0xF0F8FF),
    KNOCKBACK_SHIELD("knockback_shield", "擊退盾", "Knockback Shield", "第一次使用：舉起擊退盾；再次使用：向前衝撞，撞到人或牆就釋放衝擊波", 0x806040),
    WIND_BOMB("wind_bomb", "擊退風暴彈", "Wind Bomb", "投出風彈（可反彈牆壁 1 次，落地馬上觸發），觸發處產生衝擊波", 0xA0E0E0),
    FAKE_CRATE_LOW("fake_crate_low", "假的普通箱", "Fake Common Crate", "在前方放一個假的普通箱；打開的人會受傷、發光、震盪", 0xA07040),
    FAKE_CRATE_MID("fake_crate_mid", "假的中階箱", "Fake Mid Crate", "在前方放一個假的中階箱；打開的人會受傷、發光、震盪", 0x8090A0),
    FAKE_CRATE_HIGH("fake_crate_high", "假的高階箱", "Fake High Crate", "在前方放一個假的高階箱；打開的人會受傷、發光、震盪", 0xE0B030),
    RECON_MINE("recon_mine", "偵查地雷", "Recon Mine", "敵人靠近時，讓附近的敵人發光並顯示在地圖上（你看得到）（可以用 2 次）", 0x3070F0),
    LAND_MINE("land_mine", "地雷", "Land Mine", "敵人靠近時爆炸，附近所有人受傷並緩速（可以用 2 次）", 0x704020),
    FIRE_MINE("fire_mine", "燃燒地雷", "Fire Mine", "敵人靠近時，附近所有人燃燒 5 秒（可以用 2 次）", 0xF06020),
    SHOCK_MINE("shock_mine", "震爆地雷", "Shock Mine", "敵人靠近時產生衝擊波（可以用 2 次）", 0xF0F0F0),
    FLARE_GUN("flare_gun", "信號槍", "Flare Gun", "在你的位置召喚一個空投（會通知全體玩家）", 0xF04040),
    BLAST_SCANNER_BASIC("blast_scanner_basic", "簡略的爆破敵人偵測器", "Basic Blast Scanner", "隨機一名敵人發光（所有人都看得到，不上地圖）", 0xE0A040),
    BLAST_SCANNER("blast_scanner", "爆破敵人偵測器", "Blast Scanner", "隨機一名敵人發光並顯示在地圖上（所有人都看得到）", 0xE08020),
    BLAST_SCANNER_PLUS("blast_scanner_plus", "爆破敵人偵測器加強版", "Blast Scanner+", "其中一隊的所有敵人發光並顯示在地圖上（所有人都看得到）", 0xC05010),
    SMOKE_BURST("smoke_burst", "煙幕彈", "Smoke Burst", "以自己為中心放出半徑 8 格的平放圓柱形煙霧", 0x909090),
    TOTEM("totem", "不死圖騰", "Totem", "15 秒內副手有不死圖騰：受到致命傷時不死也不倒地，回到 50 血、額外生命 +25、恢復效果 10 秒", 0xF0D040),
    SHIELD_BUBBLE("shield_bubble", "護盾泡泡", "Shield Bubble", "15 秒內第一次受到傷害時完全擋下，並獲得 25 額外生命值與 10 秒加速", 0x60C0FF),
    WARP_PEARL("warp_pearl", "傳送珠", "Warp Pearl", "丟出不受重力的珍珠，再按一次使用鍵或飛到盡頭時，傳送到它正下方", 0x20A080),
    SILENT_BOOTS("silent_boots", "靜音靴", "Silent Boots", "15 秒內移動不會發出聲音、移動速度 +100%（不和拿刀、強心劑的速度疊加）", 0x404060),
    GAS_GRENADE("gas_grenade", "毒氣彈", "Gas Grenade", "投出毒氣彈（可反彈牆壁 1 次，落地馬上觸發），範圍內的敵人視野模糊、咳嗽、中毒", 0x60C020),
    FIRST_AID_SHOT("first_aid_shot", "急救針", "First Aid Shot", "扶隊友時：立刻扶起並回滿血｜自己倒地時：10 秒後自救｜沒倒地時：立刻回復 25 血", 0xF0F0F0),
    ARMOR_REPAIR("armor_repair", "護甲修復包", "Armor Repair Kit", "身上所有護甲的耐久度修到滿", 0x6080A0),
    ARMOR_REPAIR_PLUS("armor_repair_plus", "強化護甲修復包", "Armor Repair Kit+", "修滿所有護甲，並讓每件護甲的耐久上限 +50%（可疊加）", 0x4060C0),
    ZONE_SHELTER("zone_shelter", "圈外庇護", "Zone Shelter", "15 秒內不受圈外傷害，在圈外時移動速度 +25%", 0xC0F0C0),
    RADAR_PULSE("radar_pulse", "雷達脈衝", "Radar Pulse", "掃描 40 格內的敵人，讓他們發光並顯示在地圖上 3 秒（只有你看得到）", 0x40F0A0),
    DECOY("decoy", "殘影分身", "Decoy", "產生一個跟你一模一樣的分身，往前奔跑 10 秒", 0xA0A0F0),
    // ── 計畫書 6 E 章 ──
    BRAINWASH("brainwash", "不可控的洗腦工具", "Uncontrollable Brainwasher", "自身半徑 25 格內所有玩家（包括自己）15 秒內不能開物品欄、不能按 F 互動、不能切換快捷欄，並失去方向感", 0xC040C0),
    ROOT_ORB("root_orb", "定身球", "Root Orb", "投出（可反彈牆壁 1 次，落地馬上觸發），觸發後半徑 4.5 格內所有玩家（包括自己）3 秒不能移動（可以轉視角）", 0x50E0B0),
    FIRE_DRILL("fire_drill", "火焰鑽頭", "Fire Drill", "投出、不受重力；鑽穿方塊（最厚 7 格）到對面後持續 7 秒，對面扇形範圍內所有玩家每秒灼燒 5 點（持續 3 秒）", 0xF07020),
    RECALL("recall", "回溯點", "Recall Point", "第一次使用：記錄位置（不消耗）；20 秒內再用一次回到記錄的位置（位置有特效，所有人都看得到）。20 秒沒用自動消失", 0x40C0F0),
    GAMBLER_ROD("gambler_rod", "賭徒釣竿", "Gambler's Rod", "各 25%：掉 1 個特殊道具＋最大生命 +5｜掉 2 個＋最大生命 +10｜受 25 傷害、最大生命 -5、震盪 5 秒｜受 75 傷害、最大生命 -10、震盪 5 秒", 0xD0A040),
    RECALL_PLUS("recall_plus", "回溯點加強版", "Recall Point+", "同回溯點（20 秒內可用），另外記錄血量：回去時血量恢復成記錄時的值", 0x2080F0),
    GAMBLER_ROD_PLUS("gambler_rod_plus", "沒救的賭徒釣竿", "Hopeless Gambler's Rod", "各 25%：掉 2 個特殊道具＋最大生命 +10｜掉 4 個＋最大生命 +20｜受 50 傷害、最大生命 -10、震盪 5 秒｜受 100 傷害、最大生命 -20、震盪 5 秒", 0xB02020),
    THOR_HAMMER("thor_hammer", "雷神錘", "Thor's Hammer", "準心指的地點水平半徑 20 格內隨機落雷，每 0.2 秒一道，持續 5 秒（包括自己）", 0x80A0FF),
    THOR_HAMMER_PLUS("thor_hammer_plus", "雷神錘加強版", "Thor's Hammer+", "準心指的地點水平半徑 20 格內的所有玩家，每 0.5 秒被雷打中一次，持續 5 秒（包括自己）", 0x4060FF),
    RESKYDIVE("reskydive", "重新跳傘器", "Re-Skydiver", "（大逃殺）把自己傳送到目前位置上方 300 格，重新跳傘", 0x60B0E0),
    RESKYDIVE_GROUP("reskydive_group", "多人重新跳傘器", "Group Re-Skydiver", "（大逃殺）自身半徑 5 格內所有玩家（包括自己）傳送到你上方 300 格，重新跳傘", 0x3090C0),
    RESKYDIVE_TARGET("reskydive_target", "多人重新跳傘指定器", "Targeted Re-Skydiver", "（大逃殺）準心指的地點水平半徑 5 格內所有玩家傳送到你上方 300 格，重新跳傘", 0x2070A0),
    // ── 計畫書 9：新增 17 種特殊道具 ──
    LOOK_UP_ONE("look_up_one", "欸你看", "Hey Look!", "強制以自身半徑 20 格內距離自己最近的敵人看向最上方", 0xF0D060),
    LOOK_UP_ALL("look_up_all", "欸你們看", "Hey Everyone Look!", "強制以自身半徑 20 格內所有人（包含自己）看向最上方", 0xE0B030),
    COCKROACH("cockroach", "欸幹你手上有蟑螂", "Bug On Your Hand!", "強制以自身半徑 20 格內距離自己最近的敵人丟出手上物品", 0x784828),
    DOMAIN_EXPANSION("domain_expansion", "領域展開，但你沒抓到訣竅", "Domain Expansion (Clueless)", "展開半徑 20 格封閉領域 15 秒（出入禁止、無法破壞、子彈進不去），沒收結界內所有人（含自己）除刀外的所有物品，結束後歸還", 0x7030B0),
    FRIEND_OR_FOE("friend_or_foe", "敵我不分", "Friend or Foe", "半徑 20 格內所有玩家（含自己）15 秒內互看變史蒂夫、隱藏頭頂 ID 與隊友標記、強制開啟友軍誤傷", 0xD05080),
    PARANOIA("paranoia", "被害妄想症", "Paranoia", "半徑 20 格內所有玩家（含自己）15 秒內隨機顯示受傷方向提示與假敵人地圖紅點", 0xB02040),
    RED_LIGHT("red_light", "123木頭人", "Red Light Green Light", "半徑 20 格內所有玩家（含自己）15 秒內隨機同步倒數 3、2、1、0，數到 0 時還在移動的人受 25 傷害", 0xE06020),
    MAGNET_GRENADE("magnet_grenade", "磁人手榴彈", "Magnet Grenade", "投出（可反彈牆壁 1 次，落地馬上觸發），觸發後將半徑 5 格內的玩家吸到中心點", 0x30A0F0),
    MISFIRE_CURSE("misfire_curse", "走火詛咒", "Misfire Curse", "半徑 20 格內所有玩家（含自己）15 秒內只要拿著槍就會強制開火", 0xF05020),
    CRUDE_SWAPPER("crude_swapper", "簡陋的空間交換器", "Crude Space Swapper", "將自身半徑 20 格內的隨機兩位玩家交換位置", 0x40C0D0),
    SPACE_SWAPPER("space_swapper", "空間交換器", "Space Swapper", "將自身半徑 20 格內的所有玩家（含自己）的位置隨機交換", 0x2080E0),
    ICE_SKATES("ice_skates", "一雙溜冰鞋", "Ice Skates", "15 秒內移動放開方向鍵不再減速而是一路滑行，直到撞到方塊停下來", 0x80E0FF),
    ICE_SKATES_ALL("ice_skates_all", "送大家一雙溜冰鞋", "Ice Skates For All", "半徑 20 格內所有玩家（含自己）15 秒內移動放開方向鍵不再減速而是一路滑行，直到撞到方塊停下來", 0x50B8F0),
    HUD_JAMMER("hud_jammer", "顯示干擾器", "HUD Jammer", "半徑 20 格內所有玩家（含自己）15 秒內隱藏準心、血條、彈藥數、快捷欄、小地圖等所有抬頭顯示器", 0x505060),
    OVERHEAT_CURSE("overheat_curse", "過熱詛咒", "Overheat Curse", "半徑 20 格內所有玩家（含自己）15 秒內持續拿著同一格快捷欄物品超過 3 秒就會灼傷並震盪", 0xF08020),
    ETHEREAL("ethereal", "無實體化", "Ethereal", "5 秒內進入半透明幽靈狀態，無法攻擊也不受傷害，但可以直接穿過厚度 1 格的任何方塊", 0xB0E0FF),
    MIMIC("mimic", "擬態", "Mimic", "原地隨機變成一種物資箱，受到傷害或移動時自動解除，最多持續 60 秒", 0xC09040);

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

    /** 可以用 2 次（臨時雪牆、所有地雷；計畫書 7 C11）。剩餘次數存在物品 NBT pg_uses。 */
    public boolean twoUses() { return this == SNOW_WALL || isMine(); }

    public boolean isMine() { return this == RECON_MINE || this == LAND_MINE || this == FIRE_MINE || this == SHOCK_MINE; }

    /** 可以丟出、可反彈牆壁 1 次、落地馬上觸發的球類特殊道具（計畫書 9）。 */
    public boolean isBounceBall() {
        return this == SNOW_WALL || this == WIND_BOMB || this == GAS_GRENADE || this == ROOT_ORB || this == MAGNET_GRENADE;
    }
}
