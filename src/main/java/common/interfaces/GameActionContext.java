package common.interfaces;

import Core.Detective; // Assuming Detective is in 'core' package
import Core.DoctorWatson; // Assuming DoctorWatson is in 'core' package
import Core.Room; // Assuming Room is in 'core' package
import Core.Suspect; // Assuming Suspect is in 'core' package
import Core.TaskList; // Assuming TaskList is in 'core' package
import JsonDTO.CaseFile; // Assuming CaseFile is in 'JsonDTO' package
import common.dto.JournalEntryDTO; // Will be created in common.dto

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Defines methods needed by Commands during active gameplay.
 * This context provides an abstraction over how game actions are processed,
 * whether in single-player or multiplayer mode.
 */
public interface GameActionContext {

    // --- State Checks & Info ---
    boolean isCaseStarted();
    void setCaseStarted(boolean started);
    CaseFile getSelectedCase();
    Detective getPlayerDetective(String playerId);
    Room getCurrentRoomForPlayer(String playerId);
    String getOccupantsDescriptionInRoom(Room room, String askingPlayerId); // playerId to potentially exclude self from list
    TaskList getTaskList();
    DoctorWatson getWatson();
    List<Suspect> getAllSuspects(); // For general queries, e.g., checking all suspect locations
    Suspect getSuspectByName(String name); // For specific suspect queries

    // --- Player Actions & State Changes ---
    /**
     * Attempts to move a player in a given direction.
     * Implementations should handle sending appropriate room descriptions or updates.
     * @param playerId The ID of the player to move.
     * @param direction The direction to move (e.g., "north").
     * @return true if the move was successful, false otherwise.
     */
    boolean movePlayer(String playerId, String direction);

    /**
     * Processes a player's attempt to examine an object.
     * @param playerId The ID of the player examining.
     * @param objectName The name of the object.
     * @return A string result of the examination (description or error).
     *         Alternatively, this could directly send a DTO and return void.
     */
    String examineObjectInPlayerRoom(String playerId, String objectName); // Or returns a DTO

    /**
     * Processes a player's attempt to question a suspect.
     * @param playerId The ID of the player questioning.
     * @param suspectName The name of the suspect.
     * @return A string result of the questioning (statement or error).
     */
    String questionSuspectInPlayerRoom(String playerId, String suspectName); // Or returns a DTO

    /**
     * Processes a player's attempt to make a deduction about an object.
     * @param playerId The ID of the player deducing.
     * @param objectName The name of the object.
     * @return A string result of the deduction (clue or error).
     */
    String deduceFromObjectInPlayerRoom(String playerId, String objectName); // Or returns a DTO

    /**
     * Adds an entry to the shared or player's journal.
     * @param entry The JournalEntryDTO to add.
     */
    void addJournalEntry(JournalEntryDTO entry);

    /**
     * Retrieves journal entries.
     * @param playerId The ID of the player requesting (may be unused if journal is fully shared).
     * @return A list of JournalEntryDTOs.
     */
    List<JournalEntryDTO> getJournalEntries(String playerId);

    // --- Multiplayer Specifics / Communication (Abstracted for SP) ---
    /**
     * Sends a response DTO directly to a specific player.
     * In single-player, this might print to console.
     * @param playerId The ID of the player to send the response to.
     * @param responseDto The Serializable DTO to send.
     */
    void sendResponseToPlayer(String playerId, Serializable responseDto);

    /**
     * Broadcasts a DTO to all players in the current game session/context.
     * In single-player, this might print to console or do nothing.
     * @param dto The Serializable DTO to broadcast.
     * @param excludePlayerId Player ID to exclude from the broadcast (can be null).
     */
    void broadcastToSession(Serializable dto, String excludePlayerId);

    /**
     * Notifies other players in the session about a player's movement.
     * @param movingPlayerId The ID of the player who moved.
     * @param newRoom The new room the player entered.
     * @param oldRoom The room the player left.
     */
    void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom);


    // --- Final Exam ---
    boolean canStartFinalExam(String playerId); // Checks if conditions met (e.g., host player)
    List<CaseFile.ExamQuestion> getFinalExamQuestions();
    int submitFinalExamAnswers(String playerId, Map<String, String> answers); // Key: Question text, Value: Player's answer
    void updatePlayerRank(String playerId); // Rank evaluation logic might be internal to Detective or triggered here.

    // --- Watson ---
    String askWatsonForHint(String playerId); // Or returns a DTO

    // --- NPC Management ---
    /**
     * Triggers the logic for NPCs (Suspects, Watson) to potentially move.
     * This might be called after a player moves.
     * @param triggeringPlayerId The ID of the player whose action might trigger NPC movement.
     */
    void updateNpcMovements(String triggeringPlayerId);

    // --- Utility ---
    /**
     * Gets an identifier for the current game action context.
     * @return A string identifier (e.g., session ID).
     */
    String getContextId();


    /**
     * Processes a request from a player to update their display name.
     * @param playerId The ID of the player making the request.
     * @param newDisplayName The desired new display name.
     */
    void processUpdateDisplayName(String playerId, String newDisplayName);

    void startExamProcess(String playerId);
    /**
     * Processes a request from a player (typically guest) to start the case.
     * @param requestingPlayerId The ID of the player making the request.
     */
    void processRequestStartCase(String requestingPlayerId); // <<< NEW

    /**
     * Processes a request from a player (typically guest) to initiate the final exam.
     * @param requestingPlayerId The ID of the player making the request.
     */
    void processRequestInitiateExam(String requestingPlayerId);

    void processExamAnswer(String playerId, int questionNumber, String answerText);

    /**
     * Handles a player's request to exit the current game context.
     * In single-player, this might mean returning to a case selection menu.
     * In multiplayer, this could mean leaving a game session and returning to a lobby,
     * or disconnecting if already in a lobby.
     * The implementation is responsible for any necessary cleanup and state transitions.
     * @param playerId The ID of the player requesting to exit.
     */
    void handlePlayerExitRequest(String playerId);


}