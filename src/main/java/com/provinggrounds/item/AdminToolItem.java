package com.provinggrounds.item;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.admin.FloatText;
import com.provinggrounds.admin.SpawnPointView;
import com.provinggrounds.network.FloatTextPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: AdminToolItem.java
 * 目的: 五個管理員工具物品（計畫書 B6～B10），右鍵使用。只有 OP 或創造模式能用（伺服器端檢查），
 *   一般玩家右鍵沒有效果並提示「沒有權限」。不可堆疊，只放在創造模式「PG 管理工具」分頁與 /pg give tool，不進戰利品箱。
 */
public class AdminToolItem extends Item {

    public enum Tool {
        FLOAT_TEXT("float_text", "右鍵：輸入文字，在你的位置建立浮空文字（支援 & 色碼，最多 100 字）"),
        TEXT_ERASER("text_eraser", "右鍵：刪除離你最近的一個浮空文字"),
        SPAWN_PLACER("spawn_placer", "右鍵：在你的位置新增箱子生成點"),
        SPAWN_VIEWER("spawn_viewer", "右鍵：顯示／隱藏所有箱子生成點（只有你看得到）"),
        TEXT_UNDO("text_undo", "右鍵：復原最近一次刪除的浮空文字（再按一次復原前一筆）"),
        SPAWN_REMOVER("spawn_remover", "右鍵：刪除離你最近的一個箱子生成點（16 格內），被刪的位置會閃紅框"),
        CRATE_CONVERTER("crate_converter", "右鍵：選擇地圖，把舊資料包的箱子標記轉換成箱子生成點"),
        MAP_PAINTER("map_painter", "右鍵：選擇地圖，重新繪製大地圖／小地圖的圖片");

        public final String id;
        final String hint;

        Tool(String id, String hint) {
            this.id = id;
            this.hint = hint;
        }
    }

    private final Tool tool;

    public AdminToolItem(Tool tool) {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
        this.tool = tool;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        if (!AdminTools.allowed(p)) {
            p.sendSystemMessage(AdminTools.err("沒有權限（只有 OP 或創造模式能使用）。"));
            return InteractionResultHolder.fail(stack);
        }
        switch (tool) {
            case FLOAT_TEXT -> PGNetwork.sendTo(p, FloatTextPacket.openScreen());
            case TEXT_ERASER -> p.sendSystemMessage(FloatText.deleteNearest(p));
            case SPAWN_PLACER -> p.sendSystemMessage(AdminTools.addLootPoint(p.server, BlockPos.containing(p.position()), null));
            case SPAWN_VIEWER -> p.sendSystemMessage(SpawnPointView.toggle(p));
            case TEXT_UNDO -> p.sendSystemMessage(FloatText.undo(p.server));
            case SPAWN_REMOVER -> p.sendSystemMessage(AdminTools.removeNearestLootPoint(p));
            case CRATE_CONVERTER, MAP_PAINTER -> AdminTools.openMapPicker(p, tool);
        }
        p.getCooldowns().addCooldown(this, 5); // 避免連點
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(tool.hint).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("只有 OP 或創造模式能使用").withStyle(ChatFormatting.DARK_GRAY));
    }
}
