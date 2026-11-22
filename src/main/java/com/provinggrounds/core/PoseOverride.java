package com.provinggrounds.core;

import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientProneData;
import com.provinggrounds.modes.br.BRDowned;
import net.minecraft.world.entity.player.Player;

/**
 * 檔案: PoseOverride.java
 * 目的: 判斷玩家這一 tick 是否必須是「趴下（游泳）姿勢」：自己按鍵趴下，或倒地（趴地樣式）。
 *   由 PlayerPoseMixin 在原版決定姿勢的地方呼叫，兩端都會執行。
 *   為什麼不用 setForcedPose：TACZ 自己的匍匐功能每 tick 都會把本地玩家的強制姿勢清掉，
 *   我們設的姿勢會被蓋掉（模型、第一人稱鏡頭高度都不會變）。
 *   客戶端：用伺服器同步來的名單（ClientProneData、BRStatePacket.crawling）；伺服器：ProneManager 與倒地狀態。
 */
public final class PoseOverride {

    private PoseOverride() {}

    public static boolean mustCrawl(Player p) {
        if (p.isSpectator() || p.isPassenger()) return false; // 坐姿倒地坐在座椅上，不趴
        if (p.level().isClientSide) {
            return ClientProneData.isProne(p.getUUID()) || ClientBRState.get().crawling().contains(p.getUUID());
        }
        return ProneManager.isProne(p.getUUID()) || BRDowned.isDowned(p);
    }
}
