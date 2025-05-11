package Core; // Assuming 'core' is the package name

import java.util.HashSet;
import java.util.Set;
import java.io.Serializable; // Added for serialization

public class Detective implements Serializable {
  private static final long serialVersionUID = 1L; // For Serializable

  private final String playerId; // Changed from 'name' to 'playerId', made final
  private String rank;
  private int deduceCount;
  private int finalExamScore;
  private Room currentRoom;
  private Set<String> deducedObjects; // Tracks deduced objects by their names

  private static final String DEFAULT_RANK = "Junior Investigator";

  public Detective(String playerId) {
    if (playerId == null || playerId.trim().isEmpty()) {
      throw new IllegalArgumentException("Player ID cannot be null or empty.");
    }
    this.playerId = playerId;
    resetForNewCase(); // Initialize with default values
  }

  /**
   * Resets the detective's case-specific state for a new game or case.
   * PlayerId and currentRoom (initially) are typically managed by the game context.
   */
  public void resetForNewCase() {
    this.rank = DEFAULT_RANK;
    this.deduceCount = 0;
    this.finalExamScore = 0;
    this.deducedObjects = new HashSet<>();
    // currentRoom should be set by the game context when a case starts
  }

  /**
   * Increments the deduce count only if the object hasn't been deduced before.
   *
   * @param objectName The name of the object being deduced.
   * @return true if the deduce count was incremented, false if object was already deduced.
   */
  public boolean incrementDeduceCount(String objectName) {
    if (objectName == null || objectName.trim().isEmpty()) return false;
    if (deducedObjects.add(objectName.toLowerCase())) { // .add() returns true if not already present
      deduceCount++;
      return true;
    }
    return false;
  }

  public boolean hasDeducedObject(String objectName) {
    if (objectName == null) return false;
    return deducedObjects.contains(objectName.toLowerCase());
  }

  public int getDeduceCount() {
    return deduceCount;
  }

  public String getPlayerId() { // Renamed from getName()
    return playerId;
  }

  public String getRank() {
    return rank;
  }

  // Setter for rank might be internal if only evaluateRank changes it
  // public void setRank(String rank) { this.rank = rank; }

  public void setFinalExamScore(int score) {
    this.finalExamScore = score;
  }

  public int getFinalExamScore() {
    return finalExamScore;
  }

  public Room getCurrentRoom() {
    return currentRoom;
  }

  public void setCurrentRoom(Room room) {
    this.currentRoom = room;
  }

  /**
   * Evaluates the player's rank based on deduceCount and finalExamScore.
   * This method updates the internal rank.
   */
  public void evaluateRank() {
    // Define thresholds for rank evaluation - these can be constants
    final int SENIOR_SCORE_THRESHOLD = 3;
    final int SENIOR_DEDUCE_MAX = 5;
    final int INTERMEDIATE_SCORE_THRESHOLD = 2;
    final int INTERMEDIATE_DEDUCE_MAX = 10;

    if (finalExamScore >= SENIOR_SCORE_THRESHOLD && deduceCount <= SENIOR_DEDUCE_MAX) {
      rank = "Senior Investigator";
    } else if (finalExamScore >= INTERMEDIATE_SCORE_THRESHOLD && deduceCount <= INTERMEDIATE_DEDUCE_MAX) {
      rank = "Intermediate Investigator";
    } else {
      rank = DEFAULT_RANK; // Default or "Junior Investigator"
    }
  }

  @Override
  public String toString() {
    return "Detective{" +
            "playerId='" + playerId + '\'' +
            ", rank='" + rank + '\'' +
            ", currentRoom=" + (currentRoom != null ? currentRoom.getName() : "None") +
            '}';
  }

  // equals and hashCode if Detectives are stored in Sets or used as Map keys
  // based on playerId
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Detective detective = (Detective) o;
    return playerId.equals(detective.playerId);
  }

  @Override
  public int hashCode() {
    return playerId.hashCode();
  }
}