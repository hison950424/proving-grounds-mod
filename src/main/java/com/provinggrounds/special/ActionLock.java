package com.provinggrounds.special;

import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.BRDowned;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 檔案: ActionLock.java
 * 目的: 動作鎖定（計畫書 4 的 C 章）。所有「能不能用物品／攻擊／開槍／開箱子」的判斷都問這裡，不要各處自己判斷。
 *
 *   鎖定原因（任何一個成立就鎖定）：
 *     倒地 DOWNED       ：不能左右鍵使用物品、開槍、放置破壞方塊、開箱子；可以開背包、丟棄、吸取物品、切換快捷欄、使用特殊物品。
 *     隱形斗篷 CLOAK    ：不能開箱子、不能使用任何物品（含特殊物品、槍、補品、投擲物）；可以移動、吸取、丟棄、切換快捷欄。
 *     飛機上與剛跳機    ：不能攻擊、開槍（原本的規則）。
 *   兩端都能呼叫：客戶端只知道自己的狀態（自己的效果會同步給自己），用來在輸入層先擋，手感乾淨；伺服器才是權威。
 *
 *   攔截點：DownedEvents（互動、攻擊、放置、破壞、使用物品、開容器）、GunCompat（TACZ 開槍、換彈、近戰）、
 *           ClientSpecial（客戶端輸入層）、SpecialUse（特殊物品）。
 */
public final class ActionLock {

    private ActionLock() {}

    public static boolean cloaked(Entity e) {
        return e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.CLOAK.get());
    }

    /** 洗腦（計畫書 6 E1）：不能開物品欄、不能按 F 互動、不能切換快捷欄。 */
    public static boolean brainwashed(Entity e) {
        return e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.BRAINWASH.get());
    }

    /** 定身（計畫書 6 E2）：不能移動、跳（可以轉視角）。 */
    public static boolean rooted(Entity e) {
        return e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.ROOTED.get());
    }

    /** 不能使用一般物品、攻擊、開槍、放置破壞方塊、開箱子。 */
    public static boolean blocksActions(Entity e) {
        return BRDowned.cannotAct(e) || cloaked(e);
    }

    /** 不能使用特殊物品（倒地可以用；斗篷、飛機上不行）。伺服器端呼叫。 */
    public static boolean blocksSpecial(Player p) {
        if (cloaked(p)) return true;
        if (p.level().isClientSide) return false;
        return BattleRoyaleMode.get().isProtected(p.getUUID()) || BattleRoyaleMode.get().isAirborne(p.getUUID()); // 跳傘中也不能用（計畫書 6 D4）
    }
}
