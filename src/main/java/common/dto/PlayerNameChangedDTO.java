package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class PlayerNameChangedDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String playerId; // The actual ID of the player whose name changed
  private final String oldDisplayName;
  private final String newDisplayName;

  public PlayerNameChangedDTO(String playerId, String oldDisplayName, String newDisplayName) {
    this.playerId = Objects.requireNonNull(playerId);
    this.oldDisplayName = Objects.requireNonNull(oldDisplayName);
    this.newDisplayName = Objects.requireNonNull(newDisplayName);
  }

  public String getPlayerId() {
    return playerId;
  }

  public String getNewDisplayName() {
    return newDisplayName;
  }

  @Override
  public String toString() {
    return oldDisplayName + " is now known as " + newDisplayName + ".";
  }
}
