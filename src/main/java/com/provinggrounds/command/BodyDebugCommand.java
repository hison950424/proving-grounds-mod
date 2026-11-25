package com.provinggrounds.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.core.DisconnectBodies;
import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.arena.ArenaManager;
import com.provinggrounds.special.entity.DecoyEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: BodyDebugCommand.java
 * 目的: 掉線分身的測試指令（計畫書 7 G；單人沒辦法真的掉線測試）。管理員用。
 *   /pg debug body spawn <玩家>   不讓玩家真的掉線，直接在他的位置生成分身並讓他進入「模擬掉線」（變旁觀、模式把他當成離線）
 *   /pg debug body return <玩家>  模擬回歸（跟真的重新登入一樣的流程）
 *   /pg debug body kill <玩家>    直接擊殺他的分身
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class BodyDebugCommand {

    private BodyDebugCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pg").then(Commands.literal("debug").requires(s -> s.hasPermission(2))
            .then(Commands.literal("body")
                .then(Commands.literal("spawn").then(Commands.argument("player", EntityArgument.player()).executes(BodyDebugCommand::spawn)))
                .then(Commands.literal("return").then(Commands.argument("player", EntityArgument.player()).executes(BodyDebugCommand::back)))
                .then(Commands.literal("kill").then(Commands.argument("player", EntityArgument.player()).executes(BodyDebugCommand::kill))))));
    }

    private static int reply(CommandSourceStack src, boolean ok, String yes, String no) {
        if (ok) src.sendSuccess(() -> Component.literal("[PG] " + yes).withStyle(ChatFormatting.GREEN), true);
        else src.sendFailure(Component.literal("[PG] " + no));
        return ok ? 1 : 0;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        boolean ok = BattleRoyaleMode.get().debugGoOffline(p) || ArenaManager.debugGoOffline(p);
        return reply(ctx.getSource(), ok, "已讓 " + p.getGameProfile().getName() + " 模擬掉線（原地留下分身）",
            "沒有進行中的比賽，或這位玩家不是存活的參賽者、已經在掉線中");
    }

    private static int back(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        if (DisconnectBodies.get(p.server, p.getUUID()) == null) return reply(ctx.getSource(), false, "", "這位玩家沒有分身");
        boolean ok = BattleRoyaleMode.get().returnFromOffline(p) || ArenaManager.debugReturn(p);
        return reply(ctx.getSource(), ok, p.getGameProfile().getName() + " 已模擬回歸", "回歸失敗（比賽已經結束？）");
    }

    private static int kill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        DecoyEntity body = DisconnectBodies.bodyOf(p.server, p.getUUID());
        if (body == null) return reply(ctx.getSource(), false, "", "找不到這位玩家的分身（沒有分身、或分身所在的區塊沒有載入）");
        ServerPlayer by = ctx.getSource().getEntity() instanceof ServerPlayer s && s != p ? s : null;
        body.hurt(by != null ? body.damageSources().playerAttack(by) : body.damageSources().genericKill(), Float.MAX_VALUE);
        return reply(ctx.getSource(), true, "已擊殺 " + p.getGameProfile().getName() + " 的分身", "");
    }
}
