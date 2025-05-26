package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Data Transfer Object used to notify clients when a Non-Player Character (NPC) moves from one room
 * to another.
 */
public class NpcMovedDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L; // Standard for Serializable classes

  private final String npcName;
  private final String oldRoomName; // Can be null if it's their first appearance or if not relevant
  private final String newRoomName;

  /**
   * Constructs an NpcMovedDTO.
   *
   * @param npcName The name of the NPC that moved. Must not be null.
   * @param oldRoomName The name of the room the NPC moved from. Can be null.
   * @param newRoomName The name of the room the NPC moved to. Must not be null.
   */
  public NpcMovedDTO(String npcName, String oldRoomName, String newRoomName) {
    this.npcName = Objects.requireNonNull(npcName, "NPC name cannot be null.");
    this.oldRoomName = oldRoomName; // Nullable, e.g., if NPC is just appearing
    this.newRoomName = Objects.requireNonNull(newRoomName, "New room name cannot be null.");
  }

  /**
   * Provides a human-readable string representation of the NPC movement, suitable for display in
   * the client's console.
   *
   * @return A formatted string describing the movement.
   */
  @Override
  public String toString() {
    if (oldRoomName != null && !oldRoomName.equalsIgnoreCase(newRoomName)) {
      return npcName + " has moved from " + oldRoomName + " to " + newRoomName + ".";
    } else if (oldRoomName == null) {
      // Case for an NPC appearing for the first time or if old room is not important
      return npcName + " has appeared in " + newRoomName + ".";
    } else {
      // Case where NPC "moved" but stayed in the same room (shouldn't happen if checked before
      // sending)
      // or if oldRoomName was provided but was same as newRoomName
      return npcName + " is now in " + newRoomName + ".";
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NpcMovedDTO that = (NpcMovedDTO) o;
    return Objects.equals(npcName, that.npcName)
        && Objects.equals(oldRoomName, that.oldRoomName)
        && Objects.equals(newRoomName, that.newRoomName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(npcName, oldRoomName, newRoomName);
  }
}
