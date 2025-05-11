package common.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class GameStateData implements Serializable {
    private static final long serialVersionUID = 1L;

    // Case and Session Info
    private String caseTitle;
    private String sessionId;
    private List<String> playerIds; // The actual unique IDs of players in session
    private long lastPlayedTimestamp;
    private boolean isCaseStarted;

    // Game State
    private List<JournalEntryDTO> journalEntries;
    private int deduceCount; // Session-wide deduce count
    private Map<String, String> playerCurrentRoomNames; // playerId -> roomName
    private Map<String, String> npcCurrentRoomNames;    // npcName (e.g. "Watson", "Suspect1") -> roomName
    private Map<String, Boolean> taskCompletionStatus; // taskDescription -> isCompleted
    private Map<String, Integer> playerScores; // playerId -> finalExamScore
    private Map<String, String> playerRanks; // playerId -> rankString
    private List<String> deducedObjectsInSession; // Names of objects already deduced globally in this session

    // Constructor (can be empty or initialize collections)
    public GameStateData() {
        this.playerIds = new ArrayList<>();
        this.journalEntries = new ArrayList<>();
        this.playerCurrentRoomNames = new HashMap<>();
        this.npcCurrentRoomNames = new HashMap<>();
        this.taskCompletionStatus = new HashMap<>();
        this.playerScores = new HashMap<>();
        this.playerRanks = new HashMap<>();
        this.deducedObjectsInSession = new ArrayList<>();
    }

    // Getters
    public String getCaseTitle() { return caseTitle; }
    public String getSessionId() { return sessionId; }
    public List<String> getPlayerIds() { return new ArrayList<>(playerIds); }
    public long getLastPlayedTimestamp() { return lastPlayedTimestamp; }
    public boolean isCaseStarted() { return isCaseStarted; }
    public List<JournalEntryDTO> getJournalEntries() { return new ArrayList<>(journalEntries); }
    public int getDeduceCount() { return deduceCount; }
    public Map<String, String> getPlayerCurrentRoomNames() { return new HashMap<>(playerCurrentRoomNames); }
    public Map<String, String> getNpcCurrentRoomNames() { return new HashMap<>(npcCurrentRoomNames); }
    public Map<String, Boolean> getTaskCompletionStatus() { return new HashMap<>(taskCompletionStatus); }
    public Map<String, Integer> getPlayerScores() { return new HashMap<>(playerScores); }
    public Map<String, String> getPlayerRanks() { return new HashMap<>(playerRanks); }
    public List<String> getDeducedObjectsInSession() { return new ArrayList<>(deducedObjectsInSession); }


    // Setters
    public void setCaseTitle(String caseTitle) { this.caseTitle = caseTitle; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setPlayerIds(List<String> playerIds) { this.playerIds = new ArrayList<>(playerIds); }
    public void setLastPlayedTimestamp(long lastPlayedTimestamp) { this.lastPlayedTimestamp = lastPlayedTimestamp; }
    public void setCaseStarted(boolean caseStarted) { this.isCaseStarted = caseStarted; }
    public void setJournalEntries(List<JournalEntryDTO> journalEntries) { this.journalEntries = new ArrayList<>(journalEntries); }
    public void setDeduceCount(int deduceCount) { this.deduceCount = deduceCount; }
    public void setPlayerCurrentRoomNames(Map<String, String> playerCurrentRoomNames) { this.playerCurrentRoomNames = new HashMap<>(playerCurrentRoomNames); }
    public void setNpcCurrentRoomNames(Map<String, String> npcCurrentRoomNames) { this.npcCurrentRoomNames = new HashMap<>(npcCurrentRoomNames); }
    public void setTaskCompletionStatus(Map<String, Boolean> taskCompletionStatus) { this.taskCompletionStatus = new HashMap<>(taskCompletionStatus); }
    public void setPlayerScores(Map<String, Integer> playerScores) { this.playerScores = new HashMap<>(playerScores); }
    public void setPlayerRanks(Map<String, String> playerRanks) { this.playerRanks = new HashMap<>(playerRanks); }
    public void setDeducedObjectsInSession(List<String> deducedObjectsInSession) { this.deducedObjectsInSession = new ArrayList<>(deducedObjectsInSession); }


    @Override
    public String toString() {
        return "GameStateData{" + "sessionId='" + sessionId + '\'' + ", caseTitle='" + caseTitle + '\'' + ", players=" + playerIds.size() + '}';
    }
}