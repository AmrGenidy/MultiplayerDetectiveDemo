package server;

import Core.*;
import JsonDTO.CaseFile;
import common.commands.Command;
import common.commands.InitiateFinalExamCommand;
import common.commands.StartCaseCommand;
import common.dto.*;
import common.interfaces.GameActionContext;
import common.interfaces.GameContext;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class GameContextServer implements GameContext, GameActionContext {

  private final GameSession gameSession; // Reference back to the session for communication
  private final CaseFile
      selectedCase; // Made final as it shouldn't change post-construction for this context

  // Player specific state - managed by player IDs
  private Detective player1Detective;
  private Detective player2Detective;
  private String player1Id; // Host
  private String player2Id; // Guest

  // Shared game state
  private Map<String, Room> rooms;
  private List<Suspect> suspects;
  private DoctorWatson watson;
  private Journal<JournalEntryDTO> journal;
  private TaskList taskList;
  private boolean caseStarted = false;
  private final Random random = new Random();

  // Session-wide tracking for multiplayer mechanics
  private Set<String> deducedObjectsInSession; // Names of objects deduced by anyone in this session
  private int sessionDeduceCount;

  // Exam state for the session
  private boolean examActiveForSession = false;
  private List<CaseFile.ExamQuestion> currentExamQuestionsList;
  private Map<Integer, String> player1ExamAnswersMap; // Assuming player1 (host) submits answers
  private int currentExamQuestionIndex;

  public GameContextServer(
      GameSession gameSession, CaseFile selectedCase, String p1Id, String p2Id) {
    this.gameSession = Objects.requireNonNull(gameSession, "GameSession cannot be null");
    this.selectedCase = Objects.requireNonNull(selectedCase, "SelectedCase cannot be null");
    // Player IDs can be null initially if P2 hasn't joined when context is first created by
    // GameSession constructor
    this.player1Id = p1Id;
    this.player2Id = p2Id;

    resetForNewCaseLoad(); // Initialize all collections and states

    if (p1Id != null) this.player1Detective = new Detective(p1Id);
    if (p2Id != null) this.player2Detective = new Detective(p2Id);
  }

  // Called by GameSession when P2 joins or if context needs re-init with both players
  public void setPlayerIds(String p1Id, String p2Id) {
    this.player1Id = p1Id;
    this.player2Id = p2Id;

    if (p1Id != null) {
      if (this.player1Detective == null || !this.player1Detective.getPlayerId().equals(p1Id)) {
        this.player1Detective = new Detective(p1Id);
      }
    } else {
      this.player1Detective = null;
    }

    if (p2Id != null) {
      if (this.player2Detective == null || !this.player2Detective.getPlayerId().equals(p2Id)) {
        this.player2Detective = new Detective(p2Id);
      }
      // *** ADDED/MODIFIED: Ensure P2's room is set if game is ready ***
      if (this.selectedCase != null
          && this.selectedCase.getStartingRoom() != null
          && this.player2Detective != null) {
        Room startingRoom = getRoomByName(this.selectedCase.getStartingRoom());
        if (startingRoom != null) {
          this.player2Detective.setCurrentRoom(startingRoom);
          logGameMessage(
              "Player 2 (" + p2Id + ") position set to starting room: " + startingRoom.getName());
        } else {
          logGameMessage(
              "Warning: Could not set starting room for Player 2 ("
                  + p2Id
                  + ") upon ID set - starting room not found.");
        }
      }
    } else {
      this.player2Detective = null;
    }
    // If initializePlayerStartingState() is robust enough to handle being called multiple times
    // or if it checks if players already have rooms, you could call it here.
    // For now, direct setting is more targeted.
    // initializePlayerStartingState(); // Re-evaluate if this is needed here
  }

  public void resetForNewCaseLoad() {
    this.rooms = new HashMap<>();
    this.suspects = new ArrayList<>();
    this.journal = new Journal<>();
    this.deducedObjectsInSession = new HashSet<>();
    this.sessionDeduceCount = 0;
    this.caseStarted = false;
    this.examActiveForSession = false;
    this.currentExamQuestionsList = null;
    this.player1ExamAnswersMap = null;
    this.currentExamQuestionIndex = 0;

    if (selectedCase.getTasks() != null) {
      this.taskList = new TaskList(new ArrayList<>(selectedCase.getTasks()));
    } else {
      this.taskList = new TaskList(new ArrayList<>());
      logGameMessage("Warning: No tasks found in selected case '" + selectedCase.getTitle() + "'.");
    }
    if (selectedCase.getWatsonHints() != null && !selectedCase.getWatsonHints().isEmpty()) {
      this.watson = new DoctorWatson(new ArrayList<>(selectedCase.getWatsonHints()));
    } else {
      this.watson = new DoctorWatson(new ArrayList<>()); // Watson with no hints
      logGameMessage(
          "Warning: No Watson hints found in selected case '" + selectedCase.getTitle() + "'.");
    }

    if (player1Detective != null) player1Detective.resetForNewCase();
    if (player2Detective != null) player2Detective.resetForNewCase();
  }

  public void initializePlayerStartingState() {
    if (selectedCase.getStartingRoom() == null) {
      logGameMessage(
          "CRITICAL Error: Cannot initialize player state, selected case has no startingRoom defined.");
      gameSession.endSession(
          "Configuration error: No starting room defined."); // End session if critical
      return;
    }
    Room startingRoom = getRoomByName(selectedCase.getStartingRoom());
    if (startingRoom == null) {
      logGameMessage(
          "CRITICAL Error: Starting room '"
              + selectedCase.getStartingRoom()
              + "' (defined in case) not found after loading rooms.");
      // Attempt to use any room as a fallback, but this is a severe config issue.
      if (!rooms.isEmpty()) {
        startingRoom = rooms.values().iterator().next();
        logGameMessage(
            "Warning: Using first available room '"
                + startingRoom.getName()
                + "' as fallback starting room.");
      } else {
        logGameMessage("CRITICAL Error: No rooms loaded at all. Cannot set starting room.");
        gameSession.endSession("Configuration error: No rooms loaded."); // End session
        return;
      }
    }

    logGameMessage("Initializing player states. Starting room: " + startingRoom.getName());
    if (player1Detective != null) {
      player1Detective.resetForNewCase();
      player1Detective.setCurrentRoom(startingRoom);
    }
    if (player2Detective != null) {
      player2Detective.resetForNewCase();
      player2Detective.setCurrentRoom(startingRoom);
    }
    if (watson != null) {
      watson.setCurrentRoom(startingRoom);
    }

    // Initialize suspect positions
    if (!this.suspects.isEmpty() && !this.rooms.isEmpty()) {
      List<Room> allRoomsList = new ArrayList<>(this.rooms.values());
      final Room finalStartingRoom = startingRoom; // For lambda
      for (Suspect suspect : this.suspects) {
        List<Room> validSuspectStarts =
            allRoomsList.stream()
                .filter(
                    r ->
                        !r.getName()
                            .equalsIgnoreCase(
                                finalStartingRoom.getName())) // Try not to start in player room
                .collect(Collectors.toList());
        if (!validSuspectStarts.isEmpty()) {
          suspect.setCurrentRoom(validSuspectStarts.get(random.nextInt(validSuspectStarts.size())));
        } else { // Fallback if only one room or all rooms are starting room
          suspect.setCurrentRoom(allRoomsList.get(random.nextInt(allRoomsList.size())));
        }
      }
    }
  }

  private void logGameMessage(String message) {
    // Uses the GameSession's reference to GameServer for logging.
    this.gameSession.getServer().log("[SESS_CTX:" + gameSession.getSessionId() + "] " + message);
  }

  // --- GameContext Implementation (for Extractors) ---
  @Override
  public void addRoom(Room room) {
    if (room != null && room.getName() != null) rooms.put(room.getName().toLowerCase(), room);
    else logGameMessage("Error: Attempted to add null room or room with null name.");
  }

  @Override
  public Room getRoomByName(String name) {
    return name != null ? rooms.get(name.toLowerCase()) : null;
  }

  @Override
  public Map<String, Room> getAllRooms() {
    return Collections.unmodifiableMap(rooms);
  }

  @Override
  public void addSuspect(Suspect suspect) {
    if (suspect != null) suspects.add(suspect);
    else logGameMessage("Error: Attempted to add null suspect.");
  }

  @Override
  public void logLoadingMessage(String message) {
    logGameMessage("[LOADER] " + message);
  }

  @Override
  public String getContextIdForLog() {
    return "ServerSess-" + gameSession.getSessionId();
  }

  // --- GameActionContext Implementation (for Commands) ---
  @Override
  public boolean isCaseStarted() {
    return caseStarted;
  }

  // Inside server.GameContextServer.java

  @Override
  public void setCaseStarted(boolean started) {
    // Prevent re-entry or redundant calls if state is already set
    if (this.caseStarted == started && started) {
      logGameMessage("setCaseStarted(true) called, but case was already started.");
      // If already started, perhaps resend a minimal "game is active" confirmation to requester?
      // For now, just log and return to avoid re-broadcasting everything.
      // Client's StartCaseCommand itself might handle this by checking context.isCaseStarted()
      // first.
      return;
    }
    if (!started && !this.caseStarted) { // Trying to stop an already stopped case
      logGameMessage("setCaseStarted(false) called, but case was already not started.");
      return;
    }

    this.caseStarted = started; // Set the context's flag

    if (started) {
      logGameMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' is being started.");

      // *** NOTIFY GameSession TO UPDATE ITS STATE ***
      if (this.gameSession != null) {
        this.gameSession.setSessionState(GameSessionState.ACTIVE); // New method in GameSession
      } else {
        logGameMessage(
            "CRITICAL ERROR: gameSession is null in GameContextServer. Cannot update session state.");
        // This would be a major issue.
      }
      // *** END NOTIFICATION ***

      broadcastInitialCaseDetails(); // Now broadcast all the initial game data
    } else {
      logGameMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' has been stopped/reset (caseStarted=false).");
    }
  }

  private void broadcastInitialCaseDetails() {
    if (selectedCase == null) {
      logGameMessage("Error: Cannot broadcast initial case details, selectedCase is null.");
      broadcastToSession(
          new TextMessage("Critical error: Case data missing, cannot start.", true), null);
      return;
    }

    // 1. Broadcast Case Invitation:
    //    This is often sent by GameSession when both players join and are ready for 'start case'
    // prompt.
    //    If 'start case' command is what TRULY starts the game flow, then invitation makes more
    // sense here.
    //    Let's assume for now GameSession handled the invitation prompt BEFORE this command.
    //    If not, uncomment and send it here:
    //    broadcastToSession(new TextMessage("--- Case Invitation ---\n" +
    // selectedCase.getInvitation() +
    //        "\n\nInvestigation commencing...", false), null);

    // 2. Broadcast Case Description
    logGameMessage("Broadcasting case description...");
    broadcastToSession(
        new TextMessage("--- Case Description ---\n" + selectedCase.getDescription(), false), null);

    // 3. Broadcast Tasks
    logGameMessage("Broadcasting tasks...");
    if (taskList != null && !taskList.getTasks().isEmpty()) {
      StringBuilder taskMessage = new StringBuilder("--- Case Tasks ---\n");
      List<String> tasks = taskList.getTasks();
      for (int i = 0; i < tasks.size(); i++) {
        taskMessage.append((i + 1)).append(". ").append(tasks.get(i)).append("\n");
      }
      broadcastToSession(new TextMessage(taskMessage.toString().trim(), false), null);
    } else {
      broadcastToSession(new TextMessage("No tasks available for this case.", false), null);
    }

    // 4. Broadcast Starting Room Details
    // Both players start in the same room defined by the case.
    // Get the starting room based on player1 (could be player2, it's the same initial room for the
    // case)
    Room startingRoom;
    if (player1Detective != null
        && player1Detective.getCurrentRoom() != null) { // Check if P1 detective and room are set
      startingRoom = player1Detective.getCurrentRoom();
    } else if (player2Detective != null
        && player2Detective.getCurrentRoom() != null) { // Fallback to P2 if P1 not fully init
      startingRoom = player2Detective.getCurrentRoom();
    } else {
      // Fallback if detective rooms aren't set yet by initializePlayerStartingState
      // (which should have happened before case can be started)
      startingRoom = getRoomByName(selectedCase.getStartingRoom());
      if (startingRoom != null && player1Detective != null)
        player1Detective.setCurrentRoom(startingRoom);
      if (startingRoom != null && player2Detective != null)
        player2Detective.setCurrentRoom(startingRoom);
    }

    if (startingRoom != null) {
      logGameMessage("Broadcasting starting location: " + startingRoom.getName());
      broadcastToSession(
          new TextMessage(
              "\nYou are now at the starting location: " + startingRoom.getName(), false),
          null);

      // Create one DTO for the room and send it to both.
      // The occupants list will be from the server's perspective of who is in that room.
      List<String> objectNames =
          startingRoom.getObjects().values().stream()
              .map(GameObject::getName)
              .collect(Collectors.toList());

      List<String> occupantNamesForBroadcast = new ArrayList<>();
      // Player 1 (if in starting room - should be)
      if (player1Detective != null
          && player1Detective.getCurrentRoom() != null
          && player1Detective.getCurrentRoom().getName().equalsIgnoreCase(startingRoom.getName())) {
        ClientSession p1Session = gameSession.getClientSessionById(player1Id);
        occupantNamesForBroadcast.add(p1Session != null ? p1Session.getDisplayId() : "Player 1");
      }
      // Player 2 (if in starting room - should be)
      if (player2Detective != null
          && player2Detective.getCurrentRoom() != null
          && player2Detective.getCurrentRoom().getName().equalsIgnoreCase(startingRoom.getName())) {
        ClientSession p2Session = gameSession.getClientSessionById(player2Id);
        occupantNamesForBroadcast.add(p2Session != null ? p2Session.getDisplayId() : "Player 2");
      }
      // NPCs in starting room
      for (Suspect s : suspects) {
        if (s.getCurrentRoom() != null
            && s.getCurrentRoom().getName().equalsIgnoreCase(startingRoom.getName())) {
          occupantNamesForBroadcast.add(s.getName());
        }
      }
      if (watson != null
          && watson.getCurrentRoom() != null
          && watson.getCurrentRoom().getName().equalsIgnoreCase(startingRoom.getName())) {
        occupantNamesForBroadcast.add("Dr. Watson");
      }

      Map<String, String> exits =
          startingRoom.getNeighbors().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
      RoomDescriptionDTO initialRoomDTO =
          new RoomDescriptionDTO(
              startingRoom.getName(),
              startingRoom.getDescription(),
              objectNames,
              occupantNamesForBroadcast, // This is a general view, client 'look' might be more
              // personalized
              exits);

      broadcastToSession(initialRoomDTO, null);
    } else {
      logGameMessage("Error: Starting location could not be determined for broadcast.");
      broadcastToSession(
          new TextMessage("Error: Starting location not found for the case.", true), null);
    }

    logGameMessage("Broadcasting 'type help' message...");
    broadcastToSession(new TextMessage("\nType 'help' to see available commands.", false), null);
  }

  @Override
  public CaseFile getSelectedCase() {
    return selectedCase;
  }

  @Override
  public Detective getPlayerDetective(String playerId) {
    if (playerId == null) return null;
    if (player1Id != null && player1Id.equals(playerId)) return player1Detective;
    if (player2Id != null && player2Id.equals(playerId)) return player2Detective;
    logGameMessage("Warning: getPlayerDetective called for unknown or null playerId: " + playerId);
    return null;
  }

  @Override
  public Room getCurrentRoomForPlayer(String playerId) {
    Detective d = getPlayerDetective(playerId);
    return d != null ? d.getCurrentRoom() : null;
  }

  @Override
  public String getOccupantsDescriptionInRoom(Room room, String askingPlayerId) {
    if (room == null) {
      logGameMessage("Error: getOccupantsDescriptionInRoom called with null room.");
      return "Occupants: Error determining room";
    }
    List<String> occupantNames = new ArrayList<>();
    ClientSession p1Session = gameSession.getClientSessionById(player1Id);
    ClientSession p2Session = gameSession.getClientSessionById(player2Id);

    // Player 1
    if (player1Detective != null
        && player1Detective.getCurrentRoom() != null
        && player1Detective.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
      if (player1Id == null || !player1Id.equals(askingPlayerId)) {
        occupantNames.add(p1Session != null ? p1Session.getDisplayId() : "Player 1 (Host)");
      }
    }
    // Player 2
    if (player2Detective != null
        && player2Detective.getCurrentRoom() != null
        && player2Detective.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
      if (player2Id == null || !player2Id.equals(askingPlayerId)) {
        occupantNames.add(p2Session != null ? p2Session.getDisplayId() : "Player 2");
      }
    }
    // Suspects
    for (Suspect suspect : suspects) {
      if (suspect.getCurrentRoom() != null
          && suspect.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
        occupantNames.add(suspect.getName());
      }
    }
    // Watson
    if (watson != null
        && watson.getCurrentRoom() != null
        && watson.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
      occupantNames.add("Dr. Watson");
    }
    return occupantNames.isEmpty()
        ? "Occupants: None"
        : "Occupants: " + String.join(", ", occupantNames);
  }

  @Override
  public TaskList getTaskList() {
    return taskList;
  }

  @Override
  public DoctorWatson getWatson() {
    return watson;
  }

  @Override
  public List<Suspect> getAllSuspects() {
    return Collections.unmodifiableList(suspects);
  }

  @Override
  public boolean movePlayer(String playerId, String direction) {
    Detective movingPlayer = getPlayerDetective(playerId);
    if (movingPlayer == null) {
      logGameMessage("Error: movePlayer called for null detective (playerId: " + playerId + ")");
      sendResponseToPlayer(
          playerId,
          new TextMessage("Error: Player context not found.", true)); // Send error to player
      return false;
    }
    Room oldRoom = movingPlayer.getCurrentRoom();
    if (oldRoom == null) {
      logGameMessage(
          "Error: movePlayer called for detective not in a room (playerId: " + playerId + ")");
      sendResponseToPlayer(
          playerId, new TextMessage("Error: Your current location is unknown. Cannot move.", true));
      return false;
    }

    Room newRoom = oldRoom.getNeighbor(direction.toLowerCase());

    if (newRoom != null) {
      // 1. Move the player
      movingPlayer.setCurrentRoom(newRoom);
      logGameMessage(
          "Player "
              + playerId
              + " moved from "
              + oldRoom.getName()
              + " to "
              + newRoom.getName()
              + " (server state updated).");

      // 2. NPCs take their turn to move (AFTER player has moved)
      // The triggeringPlayerId here is the one whose move initiated this round of NPC updates.
      updateNpcMovements(playerId); // This method will now also broadcast NpcMovedDTOs

      // 3. Notify the OTHER player about the moving player's move
      // This should happen before sending the new room description to the moving player,
      // so the other player knows the context if they receive NpcMovedDTOs next.
      notifyPlayerMove(playerId, newRoom, oldRoom);

      // 4. Construct and send the RoomDescriptionDTO for the moving player's NEW room
      // This will reflect NPC positions AFTER they have moved.
      sendRoomDescriptionToPlayer(playerId, newRoom); // This helper method constructs the DTO

      return true;
    } else {
      // Player could not move in that direction
      sendResponseToPlayer(
          playerId,
          new TextMessage(
              "You can't move " + direction + " from " + oldRoom.getName() + ".", false));
      return false;
    }
  }

  private void sendRoomDescriptionToPlayer(String playerId, Room room) {
    if (room == null || playerId == null) return;
    // It constructs the DTO based on the CURRENT state of 'room' and its occupants
    List<String> objectNames =
        room.getObjects().values().stream().map(GameObject::getName).collect(Collectors.toList());

    // Get occupants description specific to the 'playerId' view for their new room
    String occupantsStr = getOccupantsDescriptionInRoom(room, playerId);
    List<String> occupantNamesList = new ArrayList<>();
    if (occupantsStr != null
        && !occupantsStr.equalsIgnoreCase("Occupants: None")
        && occupantsStr.startsWith("Occupants: ")) {
      String[] names = occupantsStr.substring("Occupants: ".length()).split(",\\s*");
      for (String name : names) {
        if (!name.trim().isEmpty()) occupantNamesList.add(name.trim());
      }
    }

    Map<String, String> exits =
        room.getNeighbors().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
    sendResponseToPlayer(
        playerId,
        new RoomDescriptionDTO(
            room.getName(), room.getDescription(), objectNames, occupantNamesList, exits));
  }

  @Override
  public void addJournalEntry(JournalEntryDTO entry) {
    if (journal.addEntry(entry)) {
      logGameMessage(
          "Journal entry added by " + entry.getContributorPlayerId() + ": " + entry.getText());
      broadcastToSession(entry, null); // Broadcast the new DTO to all players
      // Send confirmation only to the contributor
      sendResponseToPlayer(
          entry.getContributorPlayerId(),
          new TextMessage("Your note was added to the journal.", false));
    } else {
      sendResponseToPlayer(
          entry.getContributorPlayerId(),
          new TextMessage("Note was a duplicate and not added.", false));
    }
  }

  @Override
  public List<JournalEntryDTO> getJournalEntries(String playerId) {
    return journal.getEntries().stream()
        .sorted(Comparator.comparingLong(JournalEntryDTO::getTimestamp))
        .collect(Collectors.toList());
  }

  @Override
  public void sendResponseToPlayer(String playerId, Serializable responseDto) {
    ClientSession client = gameSession.getClientSessionById(playerId);
    if (client != null) {
      client.send(responseDto);
    } else {
      logGameMessage(
          "Error: Attempted to send DTO to null or disconnected client: "
              + playerId
              + ". DTO: "
              + responseDto.getClass().getSimpleName());
    }
  }

  @Override
  public void broadcastToSession(Serializable dto, String excludePlayerId) {
    gameSession.broadcast(dto, excludePlayerId);
  }

  @Override
  public void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom) {
    ClientSession otherPlayerSession = gameSession.getOtherPlayer(movingPlayerId);
    ClientSession movingPlayerSession = gameSession.getClientSessionById(movingPlayerId);

    // Ensure all objects are non-null before trying to access their properties
    if (otherPlayerSession != null
        && movingPlayerSession != null
        && oldRoom != null
        && newRoom != null) {
      otherPlayerSession.send(
          new TextMessage(
              movingPlayerSession.getDisplayId()
                  + " moved from "
                  + oldRoom.getName()
                  + " to "
                  + newRoom.getName()
                  + ".",
              false));
    }
  }

  // --- Exam Logic ---
  @Override
  public boolean canStartFinalExam(String playerId) {
    // Only player1 (host) can initiate. Case must be started. Exam not already active.
    boolean isHost = isPlayerHost(playerId);
    boolean conditionsMet = isCaseStarted() && !examActiveForSession;

    if (!isHost) {
      logGameMessage("Player " + playerId + " (guest) attempted to start exam directly. Denied.");
    }
    if (!conditionsMet) {
      logGameMessage(
          "Exam conditions not met for player "
              + playerId
              + " (isCaseStarted: "
              + isCaseStarted()
              + ", examActive: "
              + examActiveForSession
              + ")");
    }
    return isHost && conditionsMet;
  }

  @Override
  public void startExamProcess(String playerId) { // playerId is the initiator
    if (!canStartFinalExam(playerId)) { // This now checks if initiator is host
      sendResponseToPlayer(
          playerId,
          new TextMessage(
              "You cannot start the final exam at this time (not host or conditions not met).",
              true));
      return;
    }
    if (selectedCase.getFinalExam() == null || selectedCase.getFinalExam().isEmpty()) {
      broadcastToSession(
          new TextMessage("Error: No final exam questions configured for this case.", true), null);
      return;
    }

    this.examActiveForSession = true;
    this.currentExamQuestionsList = new ArrayList<>(selectedCase.getFinalExam());
    this.player1ExamAnswersMap = new HashMap<>(); // Only host's answers are stored for submission
    this.currentExamQuestionIndex = 0;

    ClientSession hostSession = gameSession.getClientSessionById(player1Id); // Host is player1Id
    String hostDisplay = hostSession != null ? hostSession.getDisplayId() : "The Host";

    logGameMessage(
        "Interactive final exam initiated by host: "
            + hostDisplay
            + ". Sending first question to all.");
    broadcastToSession(
        new TextMessage("--- Final Exam Initiated by " + hostDisplay + " ---", false), null);
    sendNextExamQuestionToSession(); // Changed from sendNextExamQuestionToHost
  }

  public boolean isPlayerHost(String playerId) {
    return this.player1Id != null && this.player1Id.equals(playerId);
  }

  // Inside server.GameContextServer.java
  @Override
  public void processRequestStartCase(String requestingPlayerId) {
    logGameMessage(
        "PROCESS_REQUEST_START_CASE: by PlayerId="
            + requestingPlayerId
            + ", CaseStarted="
            + isCaseStarted()
            + ", IsHost="
            + isPlayerHost(requestingPlayerId));

    if (isCaseStarted()) {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("The case has already started.", false));
      return;
    }
    if (isPlayerHost(requestingPlayerId)) { // If host typed "request start case"
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("As host, you can directly use the 'start case' command.", false));
      return;
    }

    // If it's a guest making the request
    if (player1Id != null) { // Check if host (player1Id) is actually connected/present
      ClientSession requestingPlayerSession = gameSession.getClientSessionById(requestingPlayerId);
      String requesterDisplay =
          (requestingPlayerSession != null)
              ? requestingPlayerSession.getDisplayId()
              : "Your partner (" + requestingPlayerId.substring(0, 4) + "..)";

      // Send prompt to HOST (player1Id)
      logGameMessage(
          "PROCESS_REQUEST_START_CASE: Sending prompt to host "
              + player1Id
              + " about request from "
              + requestingPlayerId);
      sendResponseToPlayer(
          player1Id,
          new TextMessage(
              requesterDisplay + " has requested to start the case. Type 'start case' to begin.",
              false));

      // Send confirmation to GUEST (requestingPlayerId)
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("Request sent to the host to start the case.", false));
    } else {
      logGameMessage(
          "PROCESS_REQUEST_START_CASE: Host (player1Id) is null or not available. Cannot process request from "
              + requestingPlayerId);
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("The host is not currently available to start the case.", true));
    }
  }

  @Override
  public void processRequestInitiateExam(String requestingPlayerId) {
    if (!isCaseStarted()) {
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("The case has not started yet. Cannot request exam.", true));
      return;
    }
    if (examActiveForSession) {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("An exam is already in progress.", false));
      return;
    }
    if (isPlayerHost(requestingPlayerId)) {
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("As host, you can directly use 'final exam' to initiate.", false));
      return;
    }
    // Guest is requesting
    if (player1Id != null) { // If host is present
      ClientSession requestingPlayerSession = gameSession.getClientSessionById(requestingPlayerId);
      String requesterDisplay =
          requestingPlayerSession != null ? requestingPlayerSession.getDisplayId() : "Your partner";

      sendResponseToPlayer(
          player1Id,
          new TextMessage(
              requesterDisplay
                  + " has requested to start the final exam. Type 'final exam' to initiate.",
              false));
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("Request sent to host to initiate the final exam.", false));
      logGameMessage(
          "Player "
              + requestingPlayerId
              + " requested final exam. Host "
              + player1Id
              + " notified.");
    } else {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("Host is not available to start the exam.", true));
    }
  }

  private void sendNextExamQuestionToSession() {
    logGameMessage(
        "SEND_NEXT_Q_TO_SESS: examActive="
            + examActiveForSession
            + ", CurrentIndex="
            + currentExamQuestionIndex
            + ", QuestionsListSize="
            + (currentExamQuestionsList != null ? currentExamQuestionsList.size() : "NULL_LIST"));

    if (!examActiveForSession) {
      logGameMessage("SEND_NEXT_Q_TO_SESS: Exam not active, exiting send logic.");
      return;
    }

    if (currentExamQuestionsList == null || currentExamQuestionsList.isEmpty()) {
      logGameMessage(
          "SEND_NEXT_Q_TO_SESS: CRITICAL - currentExamQuestionsList is null or empty! Cannot proceed with exam.");
      // Attempt to end exam gracefully if possible, or send error to host
      if (player1Id != null) {
        sendResponseToPlayer(
            player1Id,
            new TextMessage("Error: Exam questions are missing. Cannot continue exam.", true));
      }
      evaluateAndBroadcastExamResults(player1Id); // This will likely show 0/0 or error
      return;
    }

    if (currentExamQuestionIndex < currentExamQuestionsList.size()) {
      // Send next question
      CaseFile.ExamQuestion q = currentExamQuestionsList.get(currentExamQuestionIndex);
      ExamQuestionDTO questionDTO =
          new ExamQuestionDTO(currentExamQuestionIndex + 1, q.getQuestion());

      logGameMessage(
          "SEND_NEXT_Q_TO_SESS: Broadcasting Q_DTO for Q"
              + (currentExamQuestionIndex + 1)
              + ": \""
              + q.getQuestion().substring(0, Math.min(20, q.getQuestion().length()))
              + "...\"");
      broadcastToSession(questionDTO, null);

      // Send specific prompt to host
      if (player1Id != null) {
        logGameMessage(
            "SEND_NEXT_Q_TO_SESS: Sending prompt to host "
                + player1Id
                + " for Q"
                + (currentExamQuestionIndex + 1));
        sendResponseToPlayer(
            player1Id,
            new TextMessage(
                "Host, please submit your answer for Q" + (currentExamQuestionIndex + 1) + ".",
                false));
      }
      // Notify guest
      if (player2Id != null
          && (!player2Id.equals(player1Id))) { // Ensure guest is not also host (single player test)
        ClientSession hostSess = gameSession.getClientSessionById(player1Id);
        String hostDisp =
            (hostSess != null)
                ? hostSess.getDisplayId()
                : (player1Detective != null ? player1Detective.getPlayerId() : "The Host");
        logGameMessage(
            "SEND_NEXT_Q_TO_SESS: Notifying guest "
                + player2Id
                + " that host is answering Q"
                + (currentExamQuestionIndex + 1));
        sendResponseToPlayer(
            player2Id,
            new TextMessage(
                hostDisp
                    + " is answering exam question "
                    + (currentExamQuestionIndex + 1)
                    + "/"
                    + currentExamQuestionsList.size()
                    + "...",
                false));
      }
    } else {
      // All questions have been presented and answers collected
      logGameMessage(
          "SEND_NEXT_Q_TO_SESS: All questions ("
              + currentExamQuestionsList.size()
              + ") presented. CurrentIndex="
              + currentExamQuestionIndex
              + ". Calling evaluateAndBroadcastExamResults for host: "
              + player1Id);
      evaluateAndBroadcastExamResults(player1Id);
    }
  }

  public void processExamAnswer(String submittingPlayerId, int questionNumber, String answerText) {
    logGameMessage(
        "PROCESS_EXAM_ANSWER: Initiated by PlayerId="
            + submittingPlayerId
            + ", For QNum="
            + questionNumber
            + ", Answer=\""
            + answerText
            + "\", CurrentIndexBefore="
            + currentExamQuestionIndex
            + ", ExamActive="
            + examActiveForSession);

    if (!examActiveForSession) {
      logGameMessage(
          "PROCESS_EXAM_ANSWER: Exam not active. Sending error to " + submittingPlayerId);
      sendResponseToPlayer(submittingPlayerId, new TextMessage("Exam not active.", true));
      return;
    }

    // *** Only host (player1Id) can submit answers ***
    if (!(player1Id != null && player1Id.equals(submittingPlayerId))) {
      logGameMessage(
          "PROCESS_EXAM_ANSWER: Attempt to submit answer by non-host "
              + submittingPlayerId
              + ". Denied.");
      sendResponseToPlayer(
          submittingPlayerId, new TextMessage("Only the host can submit exam answers.", true));
      return;
    }

    // Validate questionNumber against current progress (currentExamQuestionIndex is 0-based)
    if (questionNumber != (currentExamQuestionIndex + 1)) {
      logGameMessage(
          "PROCESS_EXAM_ANSWER: Host "
              + submittingPlayerId
              + " submitted answer for Q"
              + questionNumber
              + ", but server expected Q"
              + (currentExamQuestionIndex + 1)
              + ". Resending current question.");
      sendResponseToPlayer(
          submittingPlayerId,
          new TextMessage(
              "Error: Answer submitted for an unexpected question. Please answer the current one.",
              true));

      // Resend the current expected question to the host to get them back on track
      if (currentExamQuestionsList != null
          && currentExamQuestionIndex < currentExamQuestionsList.size()) {
        CaseFile.ExamQuestion q = currentExamQuestionsList.get(currentExamQuestionIndex);
        logGameMessage(
            "PROCESS_EXAM_ANSWER: Resending Q"
                + (currentExamQuestionIndex + 1)
                + " to host "
                + player1Id);
        sendResponseToPlayer(
            player1Id, new ExamQuestionDTO(currentExamQuestionIndex + 1, q.getQuestion()));
        sendResponseToPlayer(
            player1Id,
            new TextMessage(
                "Host, please re-submit your answer for Q" + (currentExamQuestionIndex + 1) + ".",
                false));
      } else {
        logGameMessage(
            "PROCESS_EXAM_ANSWER: Cannot resend question, list empty or index out of bounds. CurrentIndex="
                + currentExamQuestionIndex);
      }
      return;
    }

    if (player1ExamAnswersMap == null) { // Should have been initialized in startExamForPlayer
      logGameMessage(
          "PROCESS_EXAM_ANSWER: CRITICAL - player1ExamAnswersMap is null! Re-initializing.");
      player1ExamAnswersMap = new HashMap<>();
    }
    player1ExamAnswersMap.put(questionNumber, answerText); // Store 1-based question number as key

    currentExamQuestionIndex++; // Advance to the next question index for the server
    logGameMessage(
        "PROCESS_EXAM_ANSWER: Stored answer for Q"
            + questionNumber
            + " by host "
            + submittingPlayerId
            + ". MapSize="
            + player1ExamAnswersMap.size()
            + ", CurrentIndexAfterIncrement="
            + currentExamQuestionIndex
            + ". Calling sendNextExamQuestionToSession().");

    sendNextExamQuestionToSession(); // Broadcast next question to all or evaluate
  }

  private void evaluateAndBroadcastExamResults(String hostPlayerId) {
    logGameMessage(
        "EVAL_EXAM_RESULTS: Starting evaluation for host "
            + hostPlayerId
            + " in session "
            + gameSession.getSessionId());

    if (currentExamQuestionsList == null) {
      logGameMessage(
          "EVAL_EXAM_RESULTS: Error - currentExamQuestionsList is null. Cannot evaluate.");
      if (player1Id != null && player1Id.equals(hostPlayerId)) {
        sendResponseToPlayer(
            player1Id, new TextMessage("Error during exam evaluation (missing questions).", true));
      }
      resetServerExamState();
      return;
    }
    if (player1ExamAnswersMap == null) {
      logGameMessage(
          "EVAL_EXAM_RESULTS: Warning - player1ExamAnswersMap is null. Assuming 0 score.");
      player1ExamAnswersMap = new HashMap<>();
    }

    int score = 0;
    List<String> reviewableAnswersDetails =
        new ArrayList<>(); // This list will be sent to the client

    for (int i = 0; i < currentExamQuestionsList.size(); i++) {
      CaseFile.ExamQuestion actualQuestion = currentExamQuestionsList.get(i);
      String actualQuestionText = actualQuestion.getQuestion();
      String correctAnswer = actualQuestion.getAnswer(); // Server knows the correct answer
      String hostAnswer = player1ExamAnswersMap.get(i + 1); // Answers stored with 1-based key

      if (hostAnswer != null && hostAnswer.equalsIgnoreCase(correctAnswer)) {
        score++;
      } else {
        // Question was answered incorrectly by the host, or not answered
        // --- MODIFICATION HERE: Do NOT include the correct answer in the DTO detail string ---
        String reviewDetail =
            String.format(
                "Q: %s\n  Your Answer: '%s'", // Removed "Correct Answer: '%s'"
                actualQuestionText, (hostAnswer != null ? hostAnswer : "Not answered"));
        // --- END MODIFICATION ---
        reviewableAnswersDetails.add(reviewDetail);
      }
    }
    logGameMessage(
        "EVAL_EXAM_RESULTS: Host "
            + hostPlayerId
            + " score: "
            + score
            + "/"
            + currentExamQuestionsList.size());

    // ... (Update ranks, determine feedback message - this part remains the same) ...
    String finalRankString = "Investigator";
    Detective hostDetective = getPlayerDetective(hostPlayerId);
    if (hostDetective != null) {
      hostDetective.setFinalExamScore(score);
      hostDetective.evaluateRank();
      finalRankString = hostDetective.getRank();
    }
    Detective guestDetective = getPlayerDetective(player2Id);
    if (guestDetective != null) {
      guestDetective.setFinalExamScore(score);
      guestDetective.evaluateRank();
    }

    String feedback;
    int totalQuestions = currentExamQuestionsList.size();
    ClientSession hostSession = gameSession.getClientSessionById(hostPlayerId);
    String hostDisplay =
        (hostSession != null)
            ? hostSession.getDisplayId()
            : (hostDetective != null ? hostDetective.getPlayerId() : "The Host");

    if (score == totalQuestions)
      feedback = "Outstanding! The case is solved perfectly by " + hostDisplay + "!";
    else if (score >= totalQuestions * 0.5)
      feedback = "Good effort by " + hostDisplay + "! Key aspects uncovered.";
    else
      feedback =
          "The mystery remains largely unsolved by "
              + hostDisplay
              + ". Further investigation was needed.";

    ExamResultDTO resultDTO =
        new ExamResultDTO(
            score,
            totalQuestions,
            feedback,
            finalRankString,
            reviewableAnswersDetails // This list now only contains question and player's wrong
            // answer
            );
    logGameMessage(
        "EVAL_EXAM_RESULTS: Broadcasting ExamResultDTO: Score="
            + score
            + ", Rank="
            + finalRankString);
    broadcastToSession(resultDTO, null);
    broadcastToSession(new TextMessage("--- Final Exam Concluded ---", false), null);

    resetServerExamState();
  }

  // Helper to reset server-side exam state variables
  private void resetServerExamState() {
    this.examActiveForSession = false;
    // It's good practice to nullify collections that are repopulated per exam,
    // rather than just clear(), to ensure fresh state.
    this.currentExamQuestionsList = null;
    this.player1ExamAnswersMap = null;
    this.currentExamQuestionIndex = 0;
    logGameMessage(
        "Server-side exam state has been reset for session " + gameSession.getSessionId());
  }

  @Override
  public void processUpdateDisplayName(String playerId, String newDisplayName) {
    ClientSession client = gameSession.getClientSessionById(playerId);
    if (client != null) {
      String oldDisplayName = client.getDisplayId();
      if (newDisplayName != null
          && !newDisplayName.equals(oldDisplayName)
          && !newDisplayName.trim().isEmpty()
          && newDisplayName.length() < 25) {
        client.setDisplayId(newDisplayName); // Update on the ClientSession object
        logGameMessage(
            "Player "
                + playerId
                + " (formerly "
                + oldDisplayName
                + ") changed display name to "
                + newDisplayName);

        // Broadcast the change to all players in the session
        PlayerNameChangedDTO pncDTO =
            new PlayerNameChangedDTO(playerId, oldDisplayName, newDisplayName);
        broadcastToSession(
            pncDTO, null); // Send to all, including the changer for confirmation sync

        // If this player is hosting a public game that's still in the lobby list,
        // the GameSessionManager needs to be notified to update the public game info.
        gameSession.notifyNameChangeToManagerIfHost(playerId, newDisplayName);

      } else {
        assert newDisplayName != null;
        if (newDisplayName.equals(oldDisplayName)) {
          // Name is the same, just confirm back to sender if needed (optional)
          sendResponseToPlayer(
              playerId,
              new TextMessage("Your display name is already " + newDisplayName + ".", false));
        } else {
          sendResponseToPlayer(
              playerId, new TextMessage("New display name is invalid or too long.", true));
        }
      }
    } else {
      logGameMessage("Error: processUpdateDisplayName received for unknown playerId: " + playerId);
    }
  }

  @Override
  public String askWatsonForHint(String playerId) {
    Detective player = getPlayerDetective(playerId);
    if (player == null) return "Error: Player context not found.";
    if (watson == null) return "Dr. Watson is not available in this case.";
    Room playerRoom = player.getCurrentRoom();
    Room watsonRoom = watson.getCurrentRoom();

    if (playerRoom == null || watsonRoom == null) {
      return "Dr. Watson's location or yours is unclear.";
    }
    if (watsonRoom.getName().equalsIgnoreCase(playerRoom.getName())) {
      String hint = watson.provideHint();
      if (hint == null || hint.trim().isEmpty()) {
        hint = "Dr. Watson seems to have no particular insight at the moment.";
      }
      // Add Watson's hint to journal for all players
      addJournalEntry(
          new JournalEntryDTO(
              "Dr. Watson's insight: " + hint, "Dr. Watson (Shared)", System.currentTimeMillis()));
      return "Watson: \"" + hint + "\"";
    }
    return "Dr. Watson is not in this room.";
  }

  @Override
  public void updateNpcMovements(String triggeringPlayerId) {
    if (!isCaseStarted()) {
      logGameMessage("NPC Movement SKIPPED (MP): Case not started.");
      return;
    }

    // For logging, get current player locations (not strictly needed for movement decision anymore)
    String p1RoomName =
        (player1Detective != null && player1Detective.getCurrentRoom() != null)
            ? player1Detective.getCurrentRoom().getName()
            : "N/A";
    String p2RoomName =
        (player2Detective != null && player2Detective.getCurrentRoom() != null)
            ? player2Detective.getCurrentRoom().getName()
            : "N/A";
    logGameMessage(
        "NPC Movement START (MP). Player1 in: " + p1RoomName + ", Player2 in: " + p2RoomName);

    // --- Move Suspects (Completely Random to any Neighbor) ---
    for (Suspect suspect : this.suspects) {
      Room oldSuspectRoom = suspect.getCurrentRoom();
      if (oldSuspectRoom == null) {
        logGameMessage("Suspect " + suspect.getName() + " is not in any room, cannot move.");
        continue;
      }

      Map<String, Room> neighbors = oldSuspectRoom.getNeighbors();
      if (neighbors.isEmpty()) {
        logGameMessage(
            "Suspect "
                + suspect.getName()
                + " in "
                + oldSuspectRoom.getName()
                + " has no neighbors, stays put.");
        continue;
      }

      // Suspect considers ALL neighbors as valid moves, regardless of player location.
      List<Room> allPossibleMoves = new ArrayList<>(neighbors.values());

      // This check is redundant if neighbors was not empty, but safe.
      if (!allPossibleMoves.isEmpty()) {
        Room newSuspectRoom = allPossibleMoves.get(random.nextInt(allPossibleMoves.size()));
        if (!newSuspectRoom
            .getName()
            .equalsIgnoreCase(oldSuspectRoom.getName())) { // Check if actually moved
          suspect.setCurrentRoom(newSuspectRoom);
          logGameMessage(
              "Suspect "
                  + suspect.getName()
                  + " moved RANDOMLY from "
                  + oldSuspectRoom.getName()
                  + " to "
                  + newSuspectRoom.getName()
                  + " (MP).");
          broadcastToSession(
              new NpcMovedDTO(
                  suspect.getName(), oldSuspectRoom.getName(), newSuspectRoom.getName()),
              null);
        }
      } else {
        logGameMessage(
            "Suspect "
                + suspect.getName()
                + " in "
                + oldSuspectRoom.getName()
                + " stays put (MP - unexpected: had neighbors but list empty).");
      }
    }

    // --- Move Watson (logic remains the same: random, can enter player rooms) ---
    if (this.watson != null) {
      Room oldWatsonRoom = this.watson.getCurrentRoom();
      if (oldWatsonRoom == null) { // Should be initialized
        if (!rooms.isEmpty()) {
          this.watson.setCurrentRoom(
              new ArrayList<>(rooms.values()).get(random.nextInt(rooms.size())));
          logGameMessage(
              "Watson was roomless (MP), placed randomly in "
                  + this.watson.getCurrentRoom().getName());
          oldWatsonRoom = this.watson.getCurrentRoom();
        } else {
          logGameMessage("Watson cannot move (MP), no rooms available.");
          return; // Exit if no rooms for Watson
        }
      }

      Map<String, Room> watsonNeighbors = oldWatsonRoom.getNeighbors();
      if (!watsonNeighbors.isEmpty()) {
        List<Room> watsonPossibleMoves = new ArrayList<>(watsonNeighbors.values());
        Room newWatsonRoom = watsonPossibleMoves.get(random.nextInt(watsonPossibleMoves.size()));
        if (!newWatsonRoom
            .getName()
            .equalsIgnoreCase(oldWatsonRoom.getName())) { // Check if actually moved
          this.watson.setCurrentRoom(newWatsonRoom);
          logGameMessage(
              "Dr. Watson moved RANDOMLY from "
                  + oldWatsonRoom.getName()
                  + " to "
                  + newWatsonRoom.getName()
                  + " (MP).");
          broadcastToSession(
              new NpcMovedDTO("Dr. Watson", oldWatsonRoom.getName(), newWatsonRoom.getName()),
              null);
        }
      } else {
        logGameMessage(
            "Dr. Watson in " + oldWatsonRoom.getName() + " has no neighbors, stays put (MP).");
      }
    } else {
      logGameMessage("Dr. Watson is not available in this case, cannot move (MP).");
    }
    logGameMessage("NPC Movement END (MP).");
  }

  // New method to handle an ExitCommand
  public void handlePlayerExitRequest(String playerId) {
    logGameMessage("Player " + playerId + " has requested to exit the game session.");
    // Delegate the actual session termination and notification to GameSession
    gameSession.playerRequestsExit(playerId);
  }

  public void executeCommand(Command command) { // This is the method called by GameSession
    if (command == null) {
      /* ... */
      return;
    }
    if (command.getPlayerId() == null) {
      /* ... */
      return;
    }

    logGameMessage(
        "Context executing command: "
            + command.getClass().getSimpleName()
            + " for player "
            + command.getPlayerId());

    // --- HOST CHECKS ---
    if (command instanceof StartCaseCommand) {
      if (!isPlayerHost(command.getPlayerId())) {
        sendResponseToPlayer(
            command.getPlayerId(),
            new TextMessage(
                "Only the host can directly start the case. Guests can use 'request start case'.",
                true));
        return;
      }
    } else if (command instanceof InitiateFinalExamCommand) {
      if (!isPlayerHost(command.getPlayerId())) {
        sendResponseToPlayer(
            command.getPlayerId(),
            new TextMessage(
                "Only the host can directly initiate the final exam. Guests can use 'request final exam'.",
                true));
        return;
      }
    }
    // --- END HOST CHECKS ---

    command.execute(this); // 'this' is the GameActionContext for the command's logic
  }

  // For saving state
  public GameStateData getGameStateForSaving() {
    GameStateData state = new GameStateData();
    state.setCaseTitle(this.selectedCase.getTitle());
    state.setSessionId(this.gameSession.getSessionId());
    List<String> pIds = new ArrayList<>();
    if (player1Id != null) pIds.add(player1Id);
    if (player2Id != null) pIds.add(player2Id);
    state.setPlayerIds(pIds);
    state.setLastPlayedTimestamp(System.currentTimeMillis());
    state.setCaseStarted(this.caseStarted);
    state.setJournalEntries(this.journal.getEntries()); // Journal now stores DTOs
    state.setDeduceCount(this.sessionDeduceCount);
    state.setDeducedObjectsInSession(new ArrayList<>(this.deducedObjectsInSession));

    Map<String, String> playerPos = new HashMap<>();
    if (player1Detective != null && player1Detective.getCurrentRoom() != null)
      playerPos.put(player1Id, player1Detective.getCurrentRoom().getName());
    if (player2Detective != null && player2Detective.getCurrentRoom() != null)
      playerPos.put(player2Id, player2Detective.getCurrentRoom().getName());
    state.setPlayerCurrentRoomNames(playerPos);

    Map<String, String> npcPos = new HashMap<>();
    if (watson != null && watson.getCurrentRoom() != null)
      npcPos.put("Watson", watson.getCurrentRoom().getName());
    for (Suspect s : suspects) {
      if (s.getCurrentRoom() != null) npcPos.put(s.getName(), s.getCurrentRoom().getName());
    }
    state.setNpcCurrentRoomNames(npcPos);

    if (this.taskList != null) {
      Map<String, Boolean> taskStatus = new HashMap<>();
      // Task completion needs a proper mechanism. For now, all false or based on a new field.
      for (String taskDesc : this.taskList.getTasks()) {
        taskStatus.put(taskDesc, false); // Placeholder: No dynamic task completion tracking yet
      }
      state.setTaskCompletionStatus(taskStatus);
    }
    Map<String, Integer> scores = new HashMap<>();
    Map<String, String> ranks = new HashMap<>();
    if (player1Detective != null && player1Id != null) { // Check player1Id for safety
      scores.put(player1Id, player1Detective.getFinalExamScore());
      ranks.put(player1Id, player1Detective.getRank());
    }
    if (player2Detective != null && player2Id != null) { // Check player2Id for safety
      scores.put(player2Id, player2Detective.getFinalExamScore());
      ranks.put(player2Id, player2Detective.getRank());
    }
    state.setPlayerScores(scores);
    state.setPlayerRanks(ranks);
    return state;
  }

  public void applyGameState(GameStateData loadedState) {
    logGameMessage("Applying loaded game state for session " + loadedState.getSessionId());
    resetForNewCaseLoad(); // Start with a clean slate but keep selectedCase

    this.caseStarted = loadedState.isCaseStarted();
    if (loadedState.getJournalEntries() != null) { // Check for null before iterating
      loadedState.getJournalEntries().forEach(this.journal::addEntry);
    }
    this.sessionDeduceCount = loadedState.getDeduceCount();
    if (loadedState.getDeducedObjectsInSession() != null) {
      this.deducedObjectsInSession = new HashSet<>(loadedState.getDeducedObjectsInSession());
    }

    // Restore NPC positions
    if (watson != null
        && loadedState.getNpcCurrentRoomNames() != null
        && loadedState.getNpcCurrentRoomNames().containsKey("Watson")) {
      watson.setCurrentRoom(getRoomByName(loadedState.getNpcCurrentRoomNames().get("Watson")));
    }
    for (Suspect s :
        suspects) { // Suspects list should be repopulated by extractors based on selectedCase
      if (loadedState.getNpcCurrentRoomNames() != null
          && loadedState.getNpcCurrentRoomNames().containsKey(s.getName())) {
        s.setCurrentRoom(getRoomByName(loadedState.getNpcCurrentRoomNames().get(s.getName())));
      }
    }

    // Player IDs (player1Id, player2Id for this context) should have been set by GameSession
    // before calling this method, based on who reconnected to the loaded game.
    if (player1Detective != null
        && loadedState.getPlayerCurrentRoomNames() != null
        && loadedState.getPlayerCurrentRoomNames().containsKey(player1Id)) {
      player1Detective.setCurrentRoom(
          getRoomByName(loadedState.getPlayerCurrentRoomNames().get(player1Id)));
      player1Detective.setFinalExamScore(
          loadedState.getPlayerScores() != null
              ? loadedState.getPlayerScores().getOrDefault(player1Id, 0)
              : 0);
      player1Detective.evaluateRank();
    }
    if (player2Detective != null
        && loadedState.getPlayerCurrentRoomNames() != null
        && loadedState.getPlayerCurrentRoomNames().containsKey(player2Id)) {
      player2Detective.setCurrentRoom(
          getRoomByName(loadedState.getPlayerCurrentRoomNames().get(player2Id)));
      player2Detective.setFinalExamScore(
          loadedState.getPlayerScores() != null
              ? loadedState.getPlayerScores().getOrDefault(player2Id, 0)
              : 0);
      player2Detective.evaluateRank();
    }
    // TODO: Apply task completion status if it becomes dynamic.
    logGameMessage("Finished applying loaded game state.");
  }
}
