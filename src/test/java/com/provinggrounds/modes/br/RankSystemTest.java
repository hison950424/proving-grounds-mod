package com.provinggrounds.modes.br;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RankSystemTest {

    @Test
    void fullLobbyFourOrMoreTeamsGivesFullPoints() {
        var d = RankSystem.calculate(RankSystem.Queue.DUO, 1000, true, 1, 4, 3, 2);
        // 吃雞 +80；戰鬥分 (3*12 + 2*6)=48, 吃雞 ×1.5 = 72；黃金門票 -20 → +132
        assertEquals(1.0, d.teamScale(), 1e-6);
        assertEquals(80, d.placementScore());
        assertEquals(72, d.combatScore());
        assertEquals(20, d.entryFee());
        assertEquals(132, d.delta());
        assertEquals(1132, d.afterRp());
    }

    @Test
    void fewerThanFourTeamsScalesDownProgressively() {
        var d3 = RankSystem.calculate(RankSystem.Queue.SOLO, 0, true, 1, 3, 0, 0);
        var d2 = RankSystem.calculate(RankSystem.Queue.SOLO, 0, true, 1, 2, 0, 0);
        var d1 = RankSystem.calculate(RankSystem.Queue.SOLO, 0, true, 1, 1, 5, 0);
        assertEquals(60, d3.delta()); // 80 * 0.75
        assertEquals(40, d2.delta()); // 80 * 0.50
        assertEquals(0, d1.delta());  // 1 隊不計分
    }

    @Test
    void demotionShieldProtectsOnceThenAllowsDemotion() {
        var shielded = RankSystem.calculate(RankSystem.Queue.SQUAD, 1005, true, 6, 8, 0, 0);
        assertEquals(1000, shielded.afterRp());
        assertTrue(shielded.shieldUsed());
        assertFalse(shielded.nextShield());
        assertEquals(RankSystem.Tier.GOLD, shielded.afterTier());

        var demoted = RankSystem.calculate(RankSystem.Queue.SQUAD, 1000, false, 6, 8, 0, 0);
        assertEquals(980, demoted.afterRp());
        assertFalse(demoted.shieldUsed());
        assertTrue(demoted.nextShield());
        assertEquals(RankSystem.Tier.SILVER, demoted.afterTier());
    }
}
