package Core;

import Core.enums.Rank;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Detective implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String playerId;
  private Rank rank; // <<< CHANGED: Now uses the Rank enum
  private int deduceCount;
  private int finalExamScore;
  private Room currentRoom;
  private Set<String> deducedObjects;

  // Default rank is now an enum constant
  private static final Rank DEFAULT_RANK_ENUM = Rank.JUNIOR_INVESTIGATOR;

  public Detective(String playerId) {
    if (playerId == null || playerId.trim().isEmpty()) {
      throw new IllegalArgumentException("Player ID cannot be null or empty.");
    }
    this.playerId = playerId;
    resetForNewCase();
  }

  public void resetForNewCase() {
    this.rank = DEFAULT_RANK_ENUM; // <<< Use enum constant
    this.deduceCount = 0;
    this.finalExamScore = 0;
    this.deducedObjects = new HashSet<>();
  }

  public boolean incrementDeduceCount(String objectName) {
    if (objectName == null || objectName.trim().isEmpty()) return false;
    if (deducedObjects.add(objectName.toLowerCase())) {
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

  public String getPlayerId() {
    return playerId;
  }

  /**
   * Gets the current rank of the detective.
   *
   * @return The Rank enum constant.
   */
  public Rank getRankEnum() { // <<< RENAMED for clarity, returns Enum
    return rank;
  }

  /**
   * Gets the display name of the detective's current rank.
   *
   * @return The string representation of the rank.
   */
  public String getRank() { // <<< KEPT for convenience, returns String display name
    return rank != null ? rank.getDisplayName() : DEFAULT_RANK_ENUM.getDisplayName();
  }

  // Internal setter for rank, if needed for direct manipulation (e.g., loading saved rank)
  // public void setRankEnum(Rank rank) { this.rank = rank; }

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

  public void evaluateRank() {
    // Define thresholds for rank evaluation
    final int SENIOR_SCORE_THRESHOLD = 3; // Example: Out of 4 questions
    final int SENIOR_DEDUCE_MAX = 2; // Example: Max 2 deductions for Senior
    final int MASTER_SCORE_THRESHOLD = 4; // Example: Perfect score for Master
    final int MASTER_DEDUCE_MAX = 1; // Example: Max 1 deduction for Master
    final int INTERMEDIATE_SCORE_THRESHOLD = 2;
    final int INTERMEDIATE_DEDUCE_MAX = 4;

    // Determine rank based on score and deductions
    // Logic can be adjusted; this is one way to order checks
    if (finalExamScore >= MASTER_SCORE_THRESHOLD && deduceCount <= MASTER_DEDUCE_MAX) {
      this.rank = Rank.MASTER_DETECTIVE;
    } else if (finalExamScore >= SENIOR_SCORE_THRESHOLD && deduceCount <= SENIOR_DEDUCE_MAX) {
      this.rank = Rank.SENIOR_INVESTIGATOR;
    } else if (finalExamScore >= INTERMEDIATE_SCORE_THRESHOLD
        && deduceCount <= INTERMEDIATE_DEDUCE_MAX) {
      this.rank = Rank.INTERMEDIATE_INVESTIGATOR;
    } else {
      this.rank = DEFAULT_RANK_ENUM; // Default to Junior Investigator
    }
  }

  @Override
  public String toString() {
    return "Detective{"
        + "playerId='"
        + playerId
        + '\''
        + ", rank='"
        + (rank != null ? rank.getDisplayName() : "N/A")
        + '\''
        + // Use display name
        ", currentRoom="
        + (currentRoom != null ? currentRoom.getName() : "None")
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Detective detective = (Detective) o;
    return Objects.equals(playerId, detective.playerId); // PlayerId is the unique identifier
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerId);
  }
}
