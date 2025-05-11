package common.dto;

import java.io.Serializable;

public class JoinGameResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final boolean success;
    private final String message;
    private final String sessionId; // If successful, the session player joined

    public JoinGameResponseDTO(boolean success, String message, String sessionId) {
        this.success = success;
        this.message = message;
        this.sessionId = sessionId; // Can be null on failure
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getSessionId() { return sessionId; }

    @Override
    public String toString() {
        return "JoinGameResponseDTO{" + "success=" + success + ", message='" + message + '\'' + ", sessionId='" + sessionId + '\'' +'}';
    }
}