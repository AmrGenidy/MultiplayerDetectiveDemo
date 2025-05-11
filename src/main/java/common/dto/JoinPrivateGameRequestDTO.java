package common.dto;

import java.io.Serializable;
import java.util.Objects;

public class JoinPrivateGameRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String gameCode;

    public JoinPrivateGameRequestDTO(String gameCode) {
        this.gameCode = Objects.requireNonNull(gameCode);
    }

    public String getGameCode() { return gameCode; }
}