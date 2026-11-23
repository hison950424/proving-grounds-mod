package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 檔案: ClientKeyDefaults.java
 * 目的: Proving Grounds 的預設按鍵（使用者指定），新版第一次開遊戲時自動套用一次（客戶端設定 default_keys_applied_v1）。
 *   套用後玩家自己在按鍵設定改的都會保留，不會再被蓋掉。
 *
 *   使用者指定：使用特殊物品 Q、丟棄 G、TACZ 切換射擊模式 B、TACZ 倍鏡倍率 V、趴下 C、物品欄（背包）Tab、
 *              靜走 Shift、蹲下 Ctrl（疾跑鍵不綁定，改成自動奔跑）。
 *   為了不撞鍵，另外調整（原本會和上面撞在一起）：
 *     TACZ 匍匐（原本 C）→ 不綁定（趴下改用本模組的 C）
 *     TACZ 近戰（原本 V，和倍鏡倍率撞）→ F；原版「主副手交換」F 已經沒有作用（副手不能放東西）→ 不綁定
 *     原版玩家列表（原本 Tab）→ `（數字 1 左邊那個鍵）
 *   找不到的按鍵（例如沒裝 TACZ）直接略過。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientKeyDefaults {

    private ClientKeyDefaults() {}

    private static boolean checked;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (checked || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        checked = true;
        try {
            if (PGClientConfig.CLIENT.defaultKeysApplied.get()) return;
        } catch (IllegalStateException e) {
            checked = false; // 設定還沒載入，下個 tick 再試
            return;
        }
        Map<String, String> want = new LinkedHashMap<>();
        want.put("key.provinggrounds.special", "key.keyboard.q");
        want.put("key.drop", "key.keyboard.g");
        want.put("key.tacz.fire_select.desc", "key.keyboard.b");
        want.put("key.tacz.zoom.desc", "key.keyboard.v");
        want.put("key.provinggrounds.prone", "key.keyboard.c");
        want.put("key.inventory", "key.keyboard.tab");
        want.put("key.provinggrounds.quiet_walk", "key.keyboard.left.shift"); // 靜走
        want.put("key.sneak", "key.keyboard.left.control");           // 蹲下
        want.put("key.sprint", "key.keyboard.unknown");               // 自動奔跑，不需要疾跑鍵
        // 避免撞鍵
        want.put("key.tacz.crawl.desc", "key.keyboard.unknown");
        want.put("key.tacz.melee.desc", "key.keyboard.f");
        want.put("key.swapOffhand", "key.keyboard.unknown");
        want.put("key.playerlist", "key.keyboard.grave.accent");

        int applied = 0;
        for (KeyMapping km : mc.options.keyMappings) {
            String key = want.get(km.getName());
            if (key == null) continue;
            km.setKeyModifierAndCode(KeyModifier.NONE, key.equals("key.keyboard.unknown") ? InputConstants.UNKNOWN : InputConstants.getKey(key));
            applied++;
        }
        KeyMapping.resetMapping();
        mc.options.save();
        PGClientConfig.CLIENT.defaultKeysApplied.set(true);
        PGClientConfig.CLIENT.defaultKeysApplied.save();
        ProvingGroundsMod.LOGGER.info("Applied Proving Grounds default key bindings ({} keys).", applied);
    }
}
