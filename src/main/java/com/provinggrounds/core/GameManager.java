package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 全域遊戲狀態機。單例，伺服器端運作。
 * 狀態：LOBBY → IN_GAME → END → LOBBY
 */
public class GameManager {

    public enum GameState { LOBBY, IN_GAME, END }
    public enum GameMode  { NONE, BATTLE_ROYALE, TDM, DOM, GK }

    private static final GameManager INSTANCE = new GameManager();
    public static GameManager get() { return INSTANCE; }

    private GameState state = GameState.LOBBY;
    private GameMode  mode  = GameMode.NONE;
    private final Set<UUID> activePlayers = new HashSet<>();

    @Nullable
    private MinecraftServer server;

    private GameManager() {}

    // ── Server reference ──────────────────────────────────────────────────────

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    @Nullable
    public MinecraftServer getServer() {
        return server;
    }

    // ── State machine ─────────────────────────────────────────────────────────

    public GameState getState() { return state; }
    public GameMode  getMode()  { return mode;  }

    public boolean isInGame()  { return state == GameState.IN_GAME; }
    public boolean isInLobby() { return state == GameState.LOBBY;   }

    /** 開始遊戲。若已在遊戲中則忽略。 */
    public boolean startGame(GameMode selectedMode) {
        if (state != GameState.LOBBY) {
            ProvingGroundsMod.LOGGER.warn("startGame called but state is {}; ignoring.", state);
            return false;
        }
        this.mode = selectedMode;
        this.state = GameState.IN_GAME;
        activePlayers.clear();
        ProvingGroundsMod.LOGGER.info("Game started: mode={}", selectedMode);
        return true;
    }

    /** 結束遊戲，進入結算。 */
    public void endGame() {
        if (state != GameState.IN_GAME) return;
        state = GameState.END;
        ProvingGroundsMod.LOGGER.info("Game ended: mode={}", mode);
    }

    /** 乾淨地重置所有狀態，回到大廳。可由管理員指令或正常結算後觸發。 */
    public void reset() {
        // 若 BR 還在跑（例如管理員中途重置），先停止並還原玩家；已結束時這行不做事
        com.provinggrounds.modes.BattleRoyaleMode.get().forceStop(false);
        com.provinggrounds.modes.arena.ArenaManager.forceStop(); // 團隊死鬥／佔點／槍王之王：還原參賽者
        state = GameState.LOBBY;
        mode  = GameMode.NONE;
        activePlayers.clear();
        TeamManager.get().reset();
        DisconnectBodies.clearAll(server); // 掉線分身與離線狀態（計畫書 7 D）
        LobbyManager.get().onReturnToLobby(server); // 觀戰者「本局結束後加入」、清掉準備狀態
        ProvingGroundsMod.LOGGER.info("Game reset to LOBBY.");
    }

    // ── Active players ────────────────────────────────────────────────────────

    public void addPlayer(ServerPlayer player) {
        activePlayers.add(player.getUUID());
    }

    public void removePlayer(ServerPlayer player) {
        activePlayers.remove(player.getUUID());
        if (state == GameState.IN_GAME && activePlayers.isEmpty()) {
            ProvingGroundsMod.LOGGER.info("All players removed; ending game.");
            endGame();
        }
    }

    public boolean isActive(ServerPlayer player) {
        return activePlayers.contains(player.getUUID());
    }

    public int getActivePlayerCount() {
        return activePlayers.size();
    }

    public Set<UUID> getActivePlayers() {
        return java.util.Collections.unmodifiableSet(activePlayers);
    }
}
