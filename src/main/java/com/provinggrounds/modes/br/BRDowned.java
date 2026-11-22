package com.provinggrounds.modes.br;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 檔案: BRDowned.java
 * 目的: 「這個玩家是否倒地」的共用判斷，伺服器與客戶端都能呼叫。
 *       伺服器：查 BattleRoyaleMode。
 *       客戶端：查伺服器送來的自己狀態（客戶端只會替自己觸發互動 / 開槍事件）。
 */
public final class BRDowned {

    private BRDowned() {}

    /** 不能開槍、近戰、換彈：倒地中，或在飛機上。兩端都能呼叫（客戶端只知道自己的狀態）。 */
    public static boolean cannotAct(Entity e) {
        if (isDowned(e)) return true;
        if (!(e instanceof Player p)) return false;
        if (p.level().isClientSide) return p.isLocalPlayer() && ClientBRState.get().selfState() == ClientBRState.SELF_PLANE;
        return BattleRoyaleMode.get().isOnPlane(p.getUUID());
    }

    public static boolean isDowned(Entity e) {
        if (!(e instanceof Player p)) return false;
        if (p.level().isClientSide) {
            // 客戶端只知道「自己」的狀態，其他玩家一律交給伺服器判斷
            return p.isLocalPlayer() && ClientBRState.get().selfState() == ClientBRState.SELF_DOWNED;
        }
        return BattleRoyaleMode.get().isDowned(p.getUUID());
    }
}
