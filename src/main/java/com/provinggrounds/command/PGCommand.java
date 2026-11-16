package com.provinggrounds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.GameManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * 管理員指令：/pg reload | reset | mode <mode> | status
 */
public class PGCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pg")
                .requires(src -> src.hasPermission(2)) // OP level 2+

                .then(Commands.literal("reload")
                    .executes(ctx -> reload(ctx.getSource())))

                .then(Commands.literal("reset")
                    .executes(ctx -> reset(ctx.getSource())))

                .then(Commands.literal("status")
                    .executes(ctx -> status(ctx.getSource())))

                .then(Commands.literal("mode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (GameManager.GameMode m : GameManager.GameMode.values()) {
                                if (m != GameManager.GameMode.NONE) builder.suggest(m.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> setMode(ctx.getSource(),
                            StringArgumentType.getString(ctx, "mode")))))
        );
    }

    private static int reload(CommandSourceStack src) {
        try {
            PGConfig.SERVER_SPEC.afterReload();
            src.sendSuccess(() -> Component.literal("[PG] 設定已重載。"), true);
            ProvingGroundsMod.LOGGER.info("Config reloaded by {}", src.getTextName());
        } catch (Exception e) {
            src.sendFailure(Component.literal("[PG] 重載失敗：" + e.getMessage()));
            ProvingGroundsMod.LOGGER.error("Config reload failed", e);
        }
        return 1;
    }

    private static int reset(CommandSourceStack src) {
        GameManager.get().reset();
        src.sendSuccess(() -> Component.literal("[PG] 遊戲狀態已重置，回到大廳。"), true);
        ProvingGroundsMod.LOGGER.info("Game reset by {}", src.getTextName());
        return 1;
    }

    private static int status(CommandSourceStack src) {
        GameManager gm = GameManager.get();
        String msg = String.format("[PG] 狀態: %s | 模式: %s | 玩家數: %d",
            gm.getState(), gm.getMode(), gm.getActivePlayerCount());
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int setMode(CommandSourceStack src, String modeName) {
        if (!GameManager.get().isInLobby()) {
            src.sendFailure(Component.literal("[PG] 只能在大廳切換模式。"));
            return 0;
        }
        try {
            GameManager.GameMode mode = GameManager.GameMode.valueOf(modeName.toUpperCase());
            if (mode == GameManager.GameMode.NONE) {
                src.sendFailure(Component.literal("[PG] 無效模式。"));
                return 0;
            }
            GameManager.get().startGame(mode);
            src.sendSuccess(() -> Component.literal("[PG] 已切換至模式: " + mode), true);
            ProvingGroundsMod.LOGGER.info("Mode set to {} by {}", mode, src.getTextName());
            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("[PG] 未知模式: " + modeName +
                "。可用：battle_royale, tdm, dom, gk"));
            return 0;
        }
    }
}
