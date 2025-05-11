package common.interfaces;

import Core.Detective;
import Core.DoctorWatson;
import Core.Room;
import Core.Suspect;
import Core.TaskList;
import JsonDTO.CaseFile;
import common.dto.JournalEntryDTO;
import java.io.Serializable;
import java.util.List;

/**
 * GameActionContext This is the main interface that commands use to interact with the game world's
 * state and logic during active gameplay. Implementations (like GameContextSinglePlayer or
 * GameContextServer) will provide the concrete logic for these actions.
 */
public interface GameActionContext {

  // --- State Checks & General Info ---
  boolean isCaseStarted();

  void setCaseStarted(boolean started); // For StartCaseCommand to trigger game start

  CaseFile getSelectedCase(); // To access case details like exam questions, description

  Detective getPlayerDetective(String playerId); // Get specific player's state object

  Room getCurrentRoomForPlayer(String playerId); // Player's current location

  // --- World Information & Interaction ---
  String getOccupantsDescriptionInRoom(Room room, String askingPlayerId); // Who is in a room?

  TaskList getTaskList();

  DoctorWatson getWatson(); // Access to Dr. Watson NPC

  List<Suspect> getAllSuspects();

  // --- Core Player Actions ---
  boolean movePlayer(String playerId, String direction);

  void addJournalEntry(JournalEntryDTO entry);

  List<JournalEntryDTO> getJournalEntries(
      String playerId); // PlayerId might be for context/filtering

  // --- Communication (Abstracted for SP/MP) ---
  void sendResponseToPlayer(String playerId, Serializable responseDto); // Send DTO to one player

  void broadcastToSession(Serializable dto, String excludePlayerId); // Send DTO to all (except one)

  void notifyPlayerMove(
      String movingPlayerId, Room newRoom, Room oldRoom); // Inform other players of movement

  // --- Exam Flow ---
  boolean canStartFinalExam(String playerId); // Can this player initiate exam? (e.g., host check)

  void startExamProcess(String playerId); // Trigger to begin sending questions one-by-one

  void processExamAnswer(
      String playerId, int questionNumber, String answerText); // Process one answer

  // --- NPC Interaction ---
  String askWatsonForHint(String playerId); // Returns hint string

  void updateNpcMovements(String triggeringPlayerId); // Trigger NPC movement phase

  // --- Multiplayer/Session Specific Requests ---
  void processRequestStartCase(String requestingPlayerId); // Guest requests host to start case

  void processRequestInitiateExam(String requestingPlayerId); // Guest requests host to start exam

  void processUpdateDisplayName(
      String playerId, String newDisplayName); // Player wants to change their name

  // --- General / Utility ---
  void handlePlayerExitRequest(String playerId); // Player wants to leave the current game/session
}
