package common.dto;

import java.io.Serializable;
import java.util.Objects;

public class JoinPublicGameRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String sessionId;

    public JoinPublicGameRequestDTO(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId);
    }

    public String getSessionId() { return sessionId; }
}