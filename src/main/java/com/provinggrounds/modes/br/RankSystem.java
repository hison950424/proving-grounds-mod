package com.provinggrounds.modes.br;

import net.minecraft.ChatFormatting;

/**
 * 檔案: RankSystem.java
 * 目的: 競技大逃殺四種獨立排位（單排／雙排／三排／四排）的段位與積分（RP）計算規則。
 *   - 7 大段位：青銅(0)、白銀(500)、黃金(1000)、白金(1600)、鑽石(2300)、大師(3100)、戰神(4000+)；
 *     入場門票依序為 0 / 10 / 20 / 30 / 40 / 50 / 60，且降段有 1 場保護。
 *   - 自適應本場總隊數：>= 4 隊為 100% 正常積分；3 隊 75%；2 隊 50%；<= 1 隊不計分。
 *   - 名次分：第 1 名 +80、第 2 名 +45、前 50% +20、後半段 0。
 *   - 戰鬥分：擊殺 +12、助攻 +6（基礎上限 72），進前 50% ×1.25、吃雞 ×1.5。
 */
public final class RankSystem {

    public enum Queue {
        SOLO(1, "solo", "單排"),
        DUO(2, "duo", "雙排"),
        TRIO(3, "trio", "三排"),
        SQUAD(4, "squad", "四排");

        public final int teamSize;
        public final String key;
        public final String displayName;

        Queue(int teamSize, String key, String displayName) {
            this.teamSize = teamSize;
            this.key = key;
            this.displayName = displayName;
        }

        public static Queue byOrdinal(int ord) {
            Queue[] all = values();
            return (ord >= 0 && ord < all.length) ? all[ord] : SOLO;
        }
    }

    public enum Tier {
        BRONZE("青銅", 0, 0, ChatFormatting.GOLD),
        SILVER("白銀", 500, 10, ChatFormatting.GRAY),
        GOLD("黃金", 1000, 20, ChatFormatting.YELLOW),
        PLATINUM("白金", 1600, 30, ChatFormatting.AQUA),
        DIAMOND("鑽石", 2300, 40, ChatFormatting.BLUE),
        MASTER("大師", 3100, 50, ChatFormatting.LIGHT_PURPLE),
        LEGEND("戰神", 4000, 60, ChatFormatting.RED);

        public final String displayName;
        public final int minRp;
        public final int entryFee;
        public final ChatFormatting color;

        Tier(String displayName, int minRp, int entryFee, ChatFormatting color) {
            this.displayName = displayName;
            this.minRp = minRp;
            this.entryFee = entryFee;
            this.color = color;
        }

        public static Tier forRp(int rp) {
            Tier[] all = values();
            for (int i = all.length - 1; i >= 0; i--) {
                if (rp >= all[i].minRp) return all[i];
            }
            return BRONZE;
        }
    }

    public record MatchDelta(
        Queue queue,
        int beforeRp,
        int afterRp,
        int delta,
        int placementScore,
        int combatScore,
        int entryFee,
        double teamScale,
        Tier beforeTier,
        Tier afterTier,
        boolean shieldUsed,
        boolean nextShield
    ) {}

    private RankSystem() {}

    /** 依本場總隊數決定積分係數：4 隊以上 100%、3 隊 75%、2 隊 50%、1 隊以下不計分。 */
    public static double teamScale(int totalTeams) {
        if (totalTeams >= 4) return 1.0;
        if (totalTeams == 3) return 0.75;
        if (totalTeams == 2) return 0.50;
        return 0.0;
    }

    public static MatchDelta calculate(Queue queue, int currentRp, boolean demotionShield,
                                       int place, int totalTeams, int kills, int assists) {
        int safeRp = Math.max(0, currentRp);
        Tier before = Tier.forRp(safeRp);
        double scale = teamScale(totalTeams);
        if (scale <= 0.0) {
            return new MatchDelta(queue, safeRp, safeRp, 0, 0, 0, 0, 0.0, before, before, false, demotionShield);
        }

        boolean topHalf = place <= (totalTeams + 1) / 2;
        int rawPlace = place == 1 ? 80 : place == 2 ? 45 : topHalf ? 20 : 0;
        int baseCombat = Math.min(72, Math.max(0, kills) * 12 + Math.max(0, assists) * 6);
        double combatMult = place == 1 ? 1.5 : topHalf ? 1.25 : 1.0;
        int rawCombat = (int) Math.round(baseCombat * combatMult);

        int scaledPlace = (int) Math.round(rawPlace * scale);
        int scaledCombat = (int) Math.round(rawCombat * scale);
        int entryFee = (int) Math.round(before.entryFee * scale);
        int rawDelta = scaledPlace + scaledCombat - entryFee;

        int afterRp = Math.max(0, safeRp + rawDelta);
        boolean shieldUsed = false;
        boolean nextShield = demotionShield;

        if (before.minRp > 0 && afterRp < before.minRp) {
            if (demotionShield) {
                afterRp = before.minRp;
                shieldUsed = true;
                nextShield = false;
            } else {
                // 正式降段後，新段位重新獲得 1 場降段保護
                nextShield = true;
            }
        } else if (afterRp > before.minRp) {
            // 只要分數高於目前段位底線（含升段），降段保護就緒
            nextShield = true;
        }

        Tier after = Tier.forRp(afterRp);
        if (after.ordinal() > before.ordinal()) {
            nextShield = true;
        }
        int actualDelta = afterRp - safeRp;
        return new MatchDelta(queue, safeRp, afterRp, actualDelta, scaledPlace, scaledCombat, entryFee, scale,
            before, after, shieldUsed, nextShield);
    }
}
