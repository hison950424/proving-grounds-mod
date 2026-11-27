package com.provinggrounds.venue;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: VenueManager.java
 * 目的: 場地選擇（M 選單 →「場地選擇」）。場地都在伺服器設定 [venues].list（預設值是舊版資料包的座標），
 *   不寫死在程式裡，之後新增場地只要加一行設定。
 *   進場時：冒險模式、最大血量 100 並補滿、恢復 I＋飽食（同舊版），再依場地旗標清物品／給無限彈藥箱。
 *   安全區（safe）玩家之間打不到；pvp 場地可以互相攻擊；respawn 場地死掉就在場內隨機重生。
 *   大逃殺的參賽者不能用場地選擇（對局結束回大廳時自動回到「大廳」）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class VenueManager {

    private static final Map<UUID, String> CURRENT = new HashMap<>();
    private static final UUID HEALTH_MOD = UUID.fromString("8c2b6f1e-5d3a-4b7c-9e21-3f4a6b8d0c15");

    private VenueManager() {}

    /** 目前設定裡的場地（依設定順序）。 */
    public static List<Venue> venues() {
        List<Venue> out = new ArrayList<>();
        try {
            for (String line : PGConfig.SERVER.venueList.get()) {
                Venue v = Venue.parse(line);
                if (v != null) out.add(v);
            }
        } catch (IllegalStateException ignored) {
            // 設定還沒載入
        }
        return out;
    }

    /** 選單上顯示的場地（不含 hidden）。 */
    public static List<Venue> visible() {
        return venues().stream().filter(v -> !v.has("hidden")).toList();
    }

    @Nullable
    public static Venue byId(String id) {
        for (Venue v : venues()) if (v.id().equals(id)) return v;
        return null;
    }

    @Nullable
    public static Venue current(ServerPlayer p) {
        String id = CURRENT.get(p.getUUID());
        return id == null ? null : byId(id);
    }

    /** 從選單選第 index 個（visible 的順序）。 */
    public static void goTo(ServerPlayer p, int index) {
        List<Venue> list = visible();
        if (index < 0 || index >= list.size()) return;
        if (BattleRoyaleMode.get().isAliveParticipant(p.getUUID()) || com.provinggrounds.modes.arena.ArenaManager.isParticipant(p.getUUID())) {
            p.displayClientMessage(Component.literal("對局中不能使用場地選擇").withStyle(ChatFormatting.RED), true);
            return;
        }
        enter(p, list.get(index), true);
    }

    /** 進入場地。 */
    public static void enter(ServerPlayer p, Venue v, boolean announce) {
        Vec3 pos = v.points().get(p.getRandom().nextInt(v.points().size()));
        if (p.isPassenger()) p.stopRiding();
        p.teleportTo(p.server.overworld(), pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
        p.setGameMode(GameType.ADVENTURE);
        CURRENT.put(p.getUUID(), v.id());
        if (v.has("clear_all")) clearCombatItems(p, true);
        else if (v.has("clear")) clearCombatItems(p, false);
        if (v.has("ammo")) giveAmmoBox(p);
        applyBuffs(p);
        if (announce) {
            p.displayClientMessage(Component.literal("[" + v.name() + "] 已傳送").withStyle(ChatFormatting.GREEN), false);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    /** 離開場地系統（例如加入大逃殺）：拿掉血量加成，忘記目前場地。 */
    public static void leave(ServerPlayer p) {
        CURRENT.remove(p.getUUID());
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(HEALTH_MOD);
    }

    /** 對局結束回大廳時：放回「大廳」場地（第一個有 clear_all 旗標的場地，沒有就第一個）。 */
    public static void backToLobby(ServerPlayer p) {
        List<Venue> list = venues();
        Venue lobby = list.stream().filter(v -> v.has("clear_all")).findFirst().orElse(list.isEmpty() ? null : list.get(0));
        if (lobby != null) CURRENT.put(p.getUUID(), lobby.id());
        applyBuffs(p);
    }

    private static void applyBuffs(ServerPlayer p) {
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(HEALTH_MOD);
            double extra = PGConfig.SERVER.venueMaxHealth.get() - attr.getBaseValue();
            if (extra > 0) attr.addTransientModifier(new AttributeModifier(HEALTH_MOD, "pg_venue_health", extra, AttributeModifier.Operation.ADDITION));
        }
        p.setHealth(p.getMaxHealth());
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
    }

    private static void clearCombatItems(ServerPlayer p, boolean all) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            var key = ForgeRegistries.ITEMS.getKey(s.getItem());
            String id = key == null ? "" : key.toString();
            boolean gunStuff = id.equals("tacz:modern_kinetic_gun") || id.equals("tacz:attachment") || id.equals("tacz:ammo_box");
            boolean lrt = id.startsWith("lrtactical:");
            if (gunStuff || (all && lrt)) inv.setItem(i, ItemStack.EMPTY);
        }
        p.inventoryMenu.broadcastChanges();
    }

    private static void giveAmmoBox(ServerPlayer p) {
        ItemStack box = com.provinggrounds.modes.br.RespawnKit.parse(PGConfig.SERVER.venueAmmoBox.get());
        if (box.isEmpty()) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameTags(inv.getItem(i), box)) return; // 已經有了
        }
        if (!inv.add(box)) p.drop(box, false);
    }

    // ── 事件 ─────────────────────────────────────────────────────────────────

    /** 安全區：非大逃殺存活參賽者不受世界邊界傷害，且場地內除 PvP 區外玩家之間打不到。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.OUTSIDE_BORDER)
                && !br.isAliveParticipant(victim.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        if (br.isAliveParticipant(victim.getUUID()) || br.isAliveParticipant(attacker.getUUID())) return;
        if (com.provinggrounds.modes.arena.ArenaManager.isParticipant(victim.getUUID())
            && com.provinggrounds.modes.arena.ArenaManager.isParticipant(attacker.getUUID())) return; // 對戰中照隊伍規則
        Venue v = current(victim), a = current(attacker);
        boolean pvp = v != null && v.has("pvp") && a != null && a.id().equals(v.id());
        if (!pvp) event.setCanceled(true);
    }

    /** respawn 場地：死掉就在場內隨機重生。 */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isEndConquered()) return;
        if (BattleRoyaleMode.get().isParticipant(p.getUUID()) || com.provinggrounds.modes.arena.ArenaManager.isParticipant(p.getUUID())) return;
        Venue v = current(p);
        if (v != null && v.has("respawn")) enter(p, v, false);
    }

    /** 登入時：不在對局中若沒有紀錄場地，依目前位置自動還原所屬場地（避免在迷你死鬥等 PvP 場地斷線重連後變成無敵安全區）。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (BattleRoyaleMode.get().isParticipant(p.getUUID()) || com.provinggrounds.modes.arena.ArenaManager.isParticipant(p.getUUID())) return;
        if (CURRENT.containsKey(p.getUUID())) return;
        Venue best = null;
        double bestDist = 80.0 * 80.0;
        for (Venue v : venues()) {
            for (Vec3 sp : v.points()) {
                double d = p.position().distanceToSqr(sp);
                if (d < bestDist) { bestDist = d; best = v; }
            }
        }
        if (best != null) CURRENT.put(p.getUUID(), best.id());
    }
}
