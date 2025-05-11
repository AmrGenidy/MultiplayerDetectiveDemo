package common.dto;

import java.io.Serializable;
import java.util.Objects;

public class ClientIdAssignmentDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String playerId; // The unique, persistent ID for the client session
    private final String assignedDisplayId; // Server might confirm or adjust the requested display ID

    public ClientIdAssignmentDTO(String playerId, String assignedDisplayId) {
        this.playerId = Objects.requireNonNull(playerId);
        this.assignedDisplayId = Objects.requireNonNull(assignedDisplayId);
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getAssignedDisplayId() {
        return assignedDisplayId;
    }

    @Override
    public String toString() {
        return "ClientIdAssignmentDTO{" +
                "playerId='" + playerId + '\'' +
                ", assignedDisplayId='" + assignedDisplayId + '\'' +
                '}';
    }
}