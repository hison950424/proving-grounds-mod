package com.provinggrounds.special;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 檔案: SpecialItem.java
 * 目的: 特殊物品（計畫書 4 的 B 章）。只能放在特殊物品欄（物品欄上方那 1 格），按「使用特殊物品」鍵（預設 Q）使用，用完就消失。
 *   物品本身沒有右鍵功能，效果全部在 SpecialUse。
 */
public class SpecialItem extends Item {

    private final SpecialType type;

    public SpecialItem(SpecialType type, Properties props) {
        super(props);
        this.type = type;
    }

    public SpecialType type() { return type; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String text = Component.translatable("item.provinggrounds." + type.id + ".desc").getString();
        tooltip.add(coloredDesc(text));
        tooltip.add(Component.translatable("tooltip.provinggrounds.special_use").withStyle(ChatFormatting.DARK_AQUA));
    }

    /**
     * 依效果性質為道具說明分段著色：
     *   - 負面效果：紅色（傷害、扣血、灼傷、中毒、沒收、禁用、減最大生命…）
     *   - 異常／控制／干擾效果：黃色（致盲、震盪、發光、衝擊波、滑行、換位、假敵人…）
     *   - 正面效果：綠色（回血、加速、隱身、護盾、修甲、自救、衝刺、空投…）
     *   - 中性說明：淺灰色
     */
    public static Component coloredDesc(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        int n = text.length();
        int segStart = 0;
        for (int i = 0; i <= n; i++) {
            boolean delim = i == n || isClauseDelim(text.charAt(i));
            if (!delim) continue;
            if (i > segStart) {
                String seg = text.substring(segStart, i);
                out.append(Component.literal(seg).withStyle(classifyEffectColor(seg)));
            }
            if (i < n) {
                out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(ChatFormatting.GRAY));
            }
            segStart = i + 1;
        }
        return out;
    }

    private static boolean isClauseDelim(char c) {
        return c == '；' || c == '｜' || c == '，' || c == '、' || c == '。';
    }

    private static final String[] NEGATIVE_KEYS = {
        "最大生命 -", "傷害", "受傷", "扣血", "灼燒", "灼傷", "燃燒", "中毒",
        "落 3 道雷", "被雷打中", "沒收", "強制開啟友軍誤傷",
        "不能開箱", "不能使用任何物品", "無法攻擊", "不能開物品欄", "不能按 F", "不能切換快捷欄",
        "不能移動", "緩速", "丟出手上物品", "掉出手上物品"
    };
    private static final String[] ABNORMAL_KEYS = {
        "致盲", "震盪", "視野模糊", "咳嗽", "衝擊波", "失去方向感", "看向最上方",
        "變史蒂夫", "隱藏頭頂", "隱藏準心", "假敵人", "倒數", "吸到中心點", "強制開火",
        "交換位置", "隨機交換", "滑行", "擬態", "半透明幽靈", "重新跳傘",
        "發光", "毒氣", "封閉領域", "反彈", "撞到牆", "觸發"
    };
    private static final String[] POSITIVE_KEYS = {
        "移動速度 +", "加速", "回復", "回滿血", "回到 50 血", "額外生命", "最大生命 +", "恢復效果",
        "自救", "立刻扶起", "不死也不倒地", "隱身", "不受圈外傷害", "不受傷害", "完全擋下",
        "耐久度修到滿", "耐久上限 +", "抗彈係數 +", "不會發出聲音", "衝刺", "雪牆",
        "舉起擊退盾", "召喚一個空投", "傳送到", "記錄位置", "回到記錄", "恢復成記錄",
        "顯示在地圖上", "分身", "穿過", "掉 1 個特殊道具", "掉 2 個", "掉 4 個",
        "擴充", "最大容量", "復活", "格擋", "安全降落", "自動配發"
    };

    private static ChatFormatting classifyEffectColor(String seg) {
        // 特例：「不受傷害」「不受圈外傷害」「不會致死」屬正面保護，不可誤判為負面
        boolean shielded = seg.contains("不受傷害") || seg.contains("不受圈外傷害") || seg.contains("不會致死");
        if (!shielded) {
            for (String k : NEGATIVE_KEYS) {
                if (seg.contains(k)) return ChatFormatting.RED;
            }
        }
        for (String k : ABNORMAL_KEYS) {
            if (seg.contains(k)) return ChatFormatting.YELLOW;
        }
        for (String k : POSITIVE_KEYS) {
            if (seg.contains(k)) return ChatFormatting.GREEN;
        }
        return ChatFormatting.GRAY;
    }

    /** 剩餘使用次數的 NBT（計畫書 7 C11：臨時雪牆、地雷可以用 2 次）。 */
    public static final String USES_TAG = "pg_uses";

    public static int usesLeft(ItemStack stack) {
        SpecialType t = typeOf(stack);
        if (t == null || !t.twoUses()) return 1;
        return stack.getTag() != null && stack.getTag().contains(USES_TAG) ? Math.max(1, stack.getTag().getInt(USES_TAG)) : 2;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = super.getName(stack);
        if (!type.twoUses()) return base;
        return base.copy().append(Component.literal(" " + usesLeft(stack) + "／2").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return type.twoUses() && usesLeft(stack) < 2; }

    @Override
    public int getBarWidth(ItemStack stack) { return Math.round(13f * usesLeft(stack) / 2f); }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55FF55; }

    @Nullable
    public static SpecialType typeOf(ItemStack stack) {
        return stack.getItem() instanceof SpecialItem s ? s.type : null;
    }
}
