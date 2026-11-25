package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientTeamColors.java
 * 目的: 隊友一律顯示藍色（使用者回饋；只在客戶端）。不管自己是哪一隊，畫面上的隊友都是藍色：
 *   頭上的名字、地圖上的隊友箭頭（MapRender）、擊殺紀錄裡的名字（HUD、KillHud）、標點（ClientPing）。
 *   敵人照原本的隊伍顏色。自己的名字（擊殺紀錄）也算隊友。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientTeamColors {

    /** 隊友的藍色（RGB）。 */
    public static final int MATE = 0x55AAFF;

    private ClientTeamColors() {}

    /** 這個名字是自己或同隊的人。 */
    public static boolean isMate(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || name == null || name.isEmpty()) return false;
        if (name.equals(mc.player.getGameProfile().getName())) return true;
        net.minecraft.world.scores.Team team = mc.player.getTeam();
        return team != null && team.getPlayers().contains(name);
    }

    /** 名字的顏色：隊友藍色，其他照原本。 */
    public static int colorFor(String name, int original) {
        return isMate(name) ? MATE : original;
    }

    /** 頭上的名字：隊友改成藍色。 */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || p == mc.player || !isMate(p.getGameProfile().getName())) return;
        event.setContent(Component.literal(p.getGameProfile().getName()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MATE))));
    }
}
