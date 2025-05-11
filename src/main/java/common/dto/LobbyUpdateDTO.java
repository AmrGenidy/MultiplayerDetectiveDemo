package common.dto;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class LobbyUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String message;
    private final List<String> playerDisplayIdsInLobbyOrGame;
    private final List<String> playerActualIdsInSession; // <<< NEW: Actual Player IDs
    private final String hostPlayerId; // <<< NEW: Actual ID of the host
    private final boolean gameStarting;

    public LobbyUpdateDTO(String message, List<String> playerDisplayIds, List<String> playerActualIds, String hostPlayerId, boolean gameStarting) {
        this.message = message;
        this.playerDisplayIdsInLobbyOrGame = playerDisplayIds != null ? new ArrayList<>(playerDisplayIds) : new ArrayList<>();
        this.playerActualIdsInSession = playerActualIds != null ? new ArrayList<>(playerActualIds) : new ArrayList<>();
        this.hostPlayerId = hostPlayerId;
        this.gameStarting = gameStarting;
    }

    public String getMessage() { return message; }
    public List<String> getPlayerDisplayIdsInLobbyOrGame() { return Collections.unmodifiableList(playerDisplayIdsInLobbyOrGame); }
    public List<String> getPlayerIdsInSession() { return Collections.unmodifiableList(playerActualIdsInSession); } // <<< NEW Getter
    public String getHostPlayerId() { return hostPlayerId; } // <<< NEW Getter
    public boolean isGameStarting() { return gameStarting; }

    @Override
    public String toString() {
        return "LobbyUpdateDTO{" + "message='" + message + '\'' + ", players=" + playerDisplayIdsInLobbyOrGame +
                ", hostId=" + hostPlayerId + ", gameStarting=" + gameStarting + '}';
    }
}