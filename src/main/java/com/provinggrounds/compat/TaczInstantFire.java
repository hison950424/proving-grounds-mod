package com.provinggrounds.compat;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 檔案: TaczInstantFire.java
 * 目的: 允許槍械在奔跑時馬上開槍，並在開槍瞬間立刻將歪斜的奔跑持槍動畫擺正。
 *   - 雙端（客戶端＋伺服器）於 PlayerTickEvent 將 ShooterDataHolder.sprintTimeS 維持為 0，
 *     消除 TACZ 的奔跑收槍開火延遲；開鏡強制切走路模式則由 ClientMovement 與 TACZ 原生開鏡邏輯保留。
 *   - 客戶端監聽 TACZ 的 GunShootEvent（在子彈射出與開火動畫觸發之前）：
 *     立刻取消奔跑、退出狀態機的 run 狀態，並將動畫控制器上正在播放或過渡中的 run／run_end 軌道
 *     以 0 秒切換回 idle 並立即更新骨架，避免「槍身還沒擺正、子彈就先往前射」的視覺脫節。
 *   - 僅以反射存取 TACZ 類別，符合 checkGunModIsolation 隔離規則。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class TaczInstantFire {

    private static boolean initAttempted;
    private static boolean ready;
    private static Method fromLiving, getDataHolder;
    private static Field sprintTimeS;

    // 客戶端開槍瞬切擺正用反射快取
    private static boolean clientHooked;
    private static volatile long lastClientShootMs;
    private static Method shootGetShooter, shootGetStack, shootGetSide;
    private static Method getGunDisplay, getStateMachine, smTrigger, smGetController;
    private static Method ctrlGetTracks, ctrlGetAnim, ctrlRunAnim, ctrlUpdate;
    private static Method runnerGetAnim, runnerGetTransitionTo;
    private static Field animNameField;
    private static Object playTypeLoop;

    private TaczInstantFire() {}

    /** 客戶端近期（300ms 內）是否剛開槍，供 ClientMovement 避免單點射擊間隔馬上切回奔跑歪槍。 */
    public static boolean recentlyShot() {
        return System.currentTimeMillis() - lastClientShootMs < 300L;
    }

    private static boolean ensureInit() {
        if (initAttempted) return ready;
        initAttempted = true;
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            fromLiving = op.getMethod("fromLivingEntity", LivingEntity.class);
            getDataHolder = op.getMethod("getDataHolder");
            sprintTimeS = Class.forName("com.tacz.guns.entity.shooter.ShooterDataHolder").getField("sprintTimeS");
            ready = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            ProvingGroundsMod.LOGGER.debug("TACZ instant sprint-fire unavailable: {}", e.toString());
        }
        return ready;
    }

    @SuppressWarnings("unchecked")
    private static void ensureClientShootHook() {
        if (clientHooked) return;
        clientHooked = true;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class<?> evCls = Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
                shootGetShooter = evCls.getMethod("getShooter");
                shootGetStack = evCls.getMethod("getGunItemStack");
                shootGetSide = evCls.getMethod("getLogicalSide");

                Class<?> apiCls = Class.forName("com.tacz.guns.api.TimelessAPI");
                getGunDisplay = apiCls.getMethod("getGunDisplay", ItemStack.class);
                Class<?> dispCls = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance");
                getStateMachine = dispCls.getMethod("getAnimationStateMachine");
                Class<?> smCls = Class.forName("com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine");
                smTrigger = smCls.getMethod("trigger", String.class);
                smGetController = smCls.getMethod("getAnimationController");

                Class<?> ctrlCls = Class.forName("com.tacz.guns.api.client.animation.AnimationController");
                Class<?> playTypeCls = Class.forName("com.tacz.guns.api.client.animation.ObjectAnimation$PlayType");
                ctrlGetTracks = ctrlCls.getMethod("getUpdatingTrackArray");
                ctrlGetAnim = ctrlCls.getMethod("getAnimation", int.class);
                ctrlRunAnim = ctrlCls.getMethod("runAnimation", int.class, String.class, playTypeCls, float.class);
                ctrlUpdate = ctrlCls.getMethod("update");
                playTypeLoop = Enum.valueOf((Class<Enum>) playTypeCls, "LOOP");

                Class<?> runnerCls = Class.forName("com.tacz.guns.api.client.animation.ObjectAnimationRunner");
                runnerGetAnim = runnerCls.getMethod("getAnimation");
                runnerGetTransitionTo = runnerCls.getMethod("getTransitionTo");
                animNameField = Class.forName("com.tacz.guns.api.client.animation.ObjectAnimation").getField("name");

                Consumer<Event> listener = TaczInstantFire::onClientGunShoot;
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class<Event>) evCls, listener);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                ProvingGroundsMod.LOGGER.debug("TACZ client shoot-snap hook unavailable: {}", e.toString());
            }
        });
    }

    private static void onClientGunShoot(Event event) {
        try {
            if (shootGetSide.invoke(event) != LogicalSide.CLIENT) return;
            Object shooter = shootGetShooter.invoke(event);
            net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
            if (local == null || shooter != local) return;

            lastClientShootMs = System.currentTimeMillis();
            if (local.isSprinting()) local.setSprinting(false);

            ItemStack stack = (ItemStack) shootGetStack.invoke(event);
            if (stack == null || stack.isEmpty()) stack = local.getMainHandItem();
            snapGunStraight(stack);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** 若槍械動畫軌道正處於 run／run_start／run_end／run_hold，立即 0 秒切回 idle 擺正槍口。 */
    private static void snapGunStraight(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null || stack.isEmpty()) return;
        Optional<?> opt = (Optional<?>) getGunDisplay.invoke(null, stack);
        if (opt == null || opt.isEmpty()) return;
        Object sm = getStateMachine.invoke(opt.get());
        if (sm == null) return;
        Object ctrl = smGetController.invoke(sm);
        if (ctrl == null) return;

        if (!hasRunningTrack(ctrl)) return;

        // 先讓 Lua 狀態機退出 run 狀態（避免下一個 tick 還在 run 狀態）
        smTrigger.invoke(sm, "idle");
        // 再將所有含 run* 動畫的軌道（含 run.exit 剛排入的 0.3s run_end）立即以 0s 過渡切回 idle 擺正
        Iterable<?> tracks = (Iterable<?>) ctrlGetTracks.invoke(ctrl);
        if (tracks != null) {
            boolean snapped = false;
            for (Object tObj : tracks) {
                if (!(tObj instanceof Integer track)) continue;
                if (isRunRunner(ctrlGetAnim.invoke(ctrl, track))) {
                    ctrlRunAnim.invoke(ctrl, track, "idle", playTypeLoop, 0.0f);
                    snapped = true;
                }
            }
            if (snapped) {
                ctrlUpdate.invoke(ctrl);
            }
        }
    }

    private static boolean hasRunningTrack(Object ctrl) throws ReflectiveOperationException {
        Iterable<?> tracks = (Iterable<?>) ctrlGetTracks.invoke(ctrl);
        if (tracks == null) return false;
        for (Object tObj : tracks) {
            if (!(tObj instanceof Integer track)) continue;
            if (isRunRunner(ctrlGetAnim.invoke(ctrl, track))) return true;
        }
        return false;
    }

    private static boolean isRunRunner(Object runner) throws ReflectiveOperationException {
        if (runner == null) return false;
        if (isRunAnimName(runnerGetAnim.invoke(runner))) return true;
        Object to = runnerGetTransitionTo.invoke(runner);
        return to != null && isRunAnimName(runnerGetAnim.invoke(to));
    }

    private static boolean isRunAnimName(Object anim) throws ReflectiveOperationException {
        if (anim == null) return false;
        Object name = animNameField.get(anim);
        return name instanceof String s && s.startsWith("run");
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player p = event.player;
        if (p == null || !ensureInit()) return;
        if (p.level().isClientSide()) ensureClientShootHook();
        if (!GunCompat.holdsGun(p)) return;
        try {
            Object op = fromLiving.invoke(null, p);
            if (op == null) return;
            Object holder = getDataHolder.invoke(op);
            if (holder == null) return;
            if (sprintTimeS.getFloat(holder) > 0f) {
                sprintTimeS.setFloat(holder, 0f);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
