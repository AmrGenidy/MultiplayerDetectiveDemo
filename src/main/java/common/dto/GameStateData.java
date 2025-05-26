package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GameStateData DTO that encapsulates all the necessary information to save and load the state of a
 * multiplayer game session.
 */
public class GameStateData implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  // --- Session & Case Identification ---
  private String caseTitle;
  private String sessionId;
  private List<String> playerIds; // Actual unique IDs of players in this session.
  private long lastPlayedTimestamp; // When this state was saved.
  private boolean isCaseStarted; // Has the 'start case' command been successfully issued?

  // --- Core Game State ---
  private List<JournalEntryDTO> journalEntries;
  private int deduceCount; // Session-wide (or could be per-player if design changes).
  private List<String> deducedObjectsInSession; // Objects already deduced in this game.

  // --- Positional Data ---
  private Map<String, String> playerCurrentRoomNames; // Key: playerId, Value: roomName.
  private Map<String, String>
      npcCurrentRoomNames; // Key: npcName (e.g., "Watson"), Value: roomName.

  // --- Progress & Scores ---
  private Map<String, Boolean>
      taskCompletionStatus; // Key: taskDescription, Value: isCompleted. (Future use)
  private Map<String, Integer> playerScores; // Key: playerId, Value: finalExamScore.
  private Map<String, String>
      playerRanks; // Key: playerId, Value: rankString. (Can be re-evaluated on load)

  /** Default constructor. Initializes collections. */
  public GameStateData() {
    // Initialize collections to prevent NullPointerExceptions later.
    this.playerIds = new ArrayList<>();
    this.journalEntries = new ArrayList<>();
    this.playerCurrentRoomNames = new HashMap<>();
    this.npcCurrentRoomNames = new HashMap<>();
    this.taskCompletionStatus = new HashMap<>();
    this.playerScores = new HashMap<>();
    this.playerRanks = new HashMap<>();
    this.deducedObjectsInSession = new ArrayList<>();
  }

  // --- Getters ---
  // Returning defensive copies or unmodifiable views for collections is good practice.
  public String getCaseTitle() {
    return caseTitle;
  }

  public String getSessionId() {
    return sessionId;
  }

  public List<String> getPlayerIds() {
    return List.copyOf(playerIds);
  }

  @SuppressWarnings("unused") // Currently unused by loading logic, kept for potential future use.
  public long getLastPlayedTimestamp() {
    return lastPlayedTimestamp;
  }

  public boolean isCaseStarted() {
    return isCaseStarted;
  }

  public List<JournalEntryDTO> getJournalEntries() {
    return List.copyOf(journalEntries);
  }

  public int getDeduceCount() {
    return deduceCount;
  }

  public List<String> getDeducedObjectsInSession() {
    return List.copyOf(deducedObjectsInSession);
  }

  public Map<String, String> getPlayerCurrentRoomNames() {
    return Map.copyOf(playerCurrentRoomNames);
  }

  public Map<String, String> getNpcCurrentRoomNames() {
    return Map.copyOf(npcCurrentRoomNames);
  }

  @SuppressWarnings("unused") // Currently unused by loading logic for dynamic tasks.
  public Map<String, Boolean> getTaskCompletionStatus() {
    return Map.copyOf(taskCompletionStatus);
  }

  public Map<String, Integer> getPlayerScores() {
    return Map.copyOf(playerScores);
  }

  @SuppressWarnings("unused") // Rank is re-evaluated on load, saved rank not directly used.
  public Map<String, String> getPlayerRanks() {
    return Map.copyOf(playerRanks);
  }

  // --- Setters ---
  // Setters take copies if the input is a collection to maintain encapsulation.
  public void setCaseTitle(String caseTitle) {
    this.caseTitle = caseTitle;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public void setPlayerIds(List<String> playerIds) {
    this.playerIds = (playerIds != null) ? new ArrayList<>(playerIds) : new ArrayList<>();
  }

  public void setLastPlayedTimestamp(long lastPlayedTimestamp) {
    this.lastPlayedTimestamp = lastPlayedTimestamp;
  }

  public void setCaseStarted(boolean caseStarted) {
    this.isCaseStarted = caseStarted;
  }

  public void setJournalEntries(List<JournalEntryDTO> journalEntries) {
    this.journalEntries =
        (journalEntries != null) ? new ArrayList<>(journalEntries) : new ArrayList<>();
  }

  public void setDeduceCount(int deduceCount) {
    this.deduceCount = deduceCount;
  }

  public void setDeducedObjectsInSession(List<String> deducedObjectsInSession) {
    this.deducedObjectsInSession =
        (deducedObjectsInSession != null)
            ? new ArrayList<>(deducedObjectsInSession)
            : new ArrayList<>();
  }

  public void setPlayerCurrentRoomNames(Map<String, String> playerCurrentRoomNames) {
    this.playerCurrentRoomNames =
        (playerCurrentRoomNames != null) ? new HashMap<>(playerCurrentRoomNames) : new HashMap<>();
  }

  public void setNpcCurrentRoomNames(Map<String, String> npcCurrentRoomNames) {
    this.npcCurrentRoomNames =
        (npcCurrentRoomNames != null) ? new HashMap<>(npcCurrentRoomNames) : new HashMap<>();
  }

  public void setTaskCompletionStatus(Map<String, Boolean> taskCompletionStatus) {
    this.taskCompletionStatus =
        (taskCompletionStatus != null) ? new HashMap<>(taskCompletionStatus) : new HashMap<>();
  }

  public void setPlayerScores(Map<String, Integer> playerScores) {
    this.playerScores = (playerScores != null) ? new HashMap<>(playerScores) : new HashMap<>();
  }

  public void setPlayerRanks(Map<String, String> playerRanks) {
    this.playerRanks = (playerRanks != null) ? new HashMap<>(playerRanks) : new HashMap<>();
  }

  @Override
  public String toString() {
    // Basic toString for quick identification in logs.
    return "GameStateData{"
        + "sessionId='"
        + (sessionId != null
            ? sessionId.substring(0, Math.min(8, sessionId.length())) + "..."
            : "N/A")
        + '\''
        + ", caseTitle='"
        + caseTitle
        + '\''
        + ", players="
        + (playerIds != null ? playerIds.size() : 0)
        + ", caseStarted="
        + isCaseStarted
        + '}';
  }
}
