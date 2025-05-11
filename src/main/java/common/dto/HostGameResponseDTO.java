package common.dto;

import java.io.Serializable;

public class HostGameResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final boolean success;
    private final String message;
    private final String gameCode; // For private games
    private final String sessionId; // Server-generated session ID

    public HostGameResponseDTO(boolean success, String message, String gameCode, String sessionId) {
        this.success = success;
        this.message = message;
        this.gameCode = gameCode; // Can be null if not applicable (e.g. public game or failure)
        this.sessionId = sessionId; // Can be null on failure
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getGameCode() { return gameCode; }
    public String getSessionId() { return sessionId; }

    @Override
    public String toString() {
        return "HostGameResponseDTO{" + "success=" + success + ", message='" + message + '\'' + ", gameCode='" + gameCode + '\'' + ", sessionId='" + sessionId + '\'' + '}';
    }
}