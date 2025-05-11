package singleplayer;

import Core.*;
import JsonDTO.CaseFile;
import common.dto.*;
import common.interfaces.GameActionContext;
import common.interfaces.GameContext;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class GameContextSinglePlayer implements GameContext, GameActionContext {

  // --- Game State Fields ---
  private Detective detective;
  private DoctorWatson watson;
  private Map<String, Room> rooms;
  private List<Suspect> suspects;
  private Journal<JournalEntryDTO> journal; // Using the generic Journal with DTOs
  private TaskList taskList;
  private CaseFile selectedCase;
  private Room currentRoom; // Player's current room

  private boolean caseStarted = false;
  private final Random random = new Random();

  // Flags for game flow control
  private boolean wantsToExitToCaseSelection = false;
  private boolean wantsToExitApplication = false;

  // Exam state fields
  private List<CaseFile.ExamQuestion> currentExamQuestionsList;
  private Map<Integer, String> playerAnswersMap;
  private int currentQuestionIndex; // 0-based
  private boolean isExamActive;

  // awaitingAnswerForQuestionNumber removed, use isAwaitingExamAnswer() and
  // getAwaitingQuestionNumber() helpers

  public GameContextSinglePlayer() {
    this.detective = new Detective("PlayerDetectiveSP"); // Default ID for SP
    this.rooms = new HashMap<>();
    this.suspects = new ArrayList<>();
    this.journal = new Journal<>();
    resetExamState(); // Initialize exam state
  }

  // *** NEW METHOD: resetForNewCaseLoad ***
  /**
   * Resets the context state in preparation for loading a new case. Clears rooms, suspects,
   * journal, resets flags and exam state. Keeps the detective object but resets its internal state.
   */
  public void resetForNewCaseLoad() {
    logContextMessage("Resetting context for new case load.");
    this.rooms.clear();
    this.suspects.clear();
    this.journal.clearEntries();
    this.taskList = null; // Will be reloaded
    this.selectedCase = null;
    this.currentRoom = null; // Will be set during init
    this.caseStarted = false;
    this.wantsToExitToCaseSelection = false;
    this.wantsToExitApplication = false;
    this.watson = null; // Will be reloaded

    if (this.detective != null) {
      this.detective.resetForNewCase();
    } else {
      // Should not happen if constructor is called, but defensive
      this.detective = new Detective("PlayerDetectiveSP_Fallback");
    }
    resetExamState(); // Clear any previous exam progress
  }

  private void resetExamState() {
    this.isExamActive = false;
    this.currentExamQuestionsList = null;
    this.playerAnswersMap = null;
    this.currentQuestionIndex = 0;
  }

  private void broadcastInitialCaseDetailsToPlayer(String playerId) {
    if (selectedCase == null) {
      sendResponseToPlayer(
          playerId, new TextMessage("Error: No case selected to display details.", true));
      return;
    }

    // 1. Send Case Description
    sendResponseToPlayer(
        playerId,
        new TextMessage("--- Case Description ---\n" + selectedCase.getDescription(), false));

    // 2. Send Tasks
    if (taskList != null && !taskList.getTasks().isEmpty()) {
      StringBuilder taskMessage = new StringBuilder("--- Case Tasks ---\n");
      List<String> tasks = taskList.getTasks();
      for (int i = 0; i < tasks.size(); i++) {
        taskMessage.append((i + 1)).append(". ").append(tasks.get(i)).append("\n");
      }
      sendResponseToPlayer(playerId, new TextMessage(taskMessage.toString().trim(), false));
    } else {
      sendResponseToPlayer(playerId, new TextMessage("No tasks available for this case.", false));
    }

    // 3. Send Starting Room Details
    Room spCurrentRoom = getCurrentRoomForPlayer(playerId); // Get the actual current room for SP
    if (spCurrentRoom != null) {
      sendResponseToPlayer(
          playerId,
          new TextMessage(
              "\nYou are now at the starting location: " + spCurrentRoom.getName(), false));
      sendResponseToPlayer(
          playerId, createRoomDescriptionDTO(spCurrentRoom, playerId)); // Use existing helper
    } else {
      sendResponseToPlayer(
          playerId,
          new TextMessage("Error: Starting location not found or player not placed.", true));
    }
    sendResponseToPlayer(
        playerId, new TextMessage("\nType 'help' to see available commands.", false));
  }

  @Override
  public void setCaseStarted(boolean started) {
    if (this.caseStarted == started && started) {
      logContextMessage(
          "setCaseStarted called with "
              + started
              + ", but caseStarted is already "
              + this.caseStarted);
      return;
    }
    if (!started && !this.caseStarted) {
      logContextMessage("setCaseStarted called with false, but case already not started.");
      return;
    }

    this.caseStarted = started;
    if (started) {
      logContextMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' has been started.");
      // *** This is where the initial case info should be "broadcast" (i.e., printed) for SP ***
      broadcastInitialCaseDetailsToPlayer(
          this.detective.getPlayerId()); // Pass the SP detective's ID
    } else {
      logContextMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' has been stopped/reset.");
    }
  }

  @Override
  public void processUpdateDisplayName(String playerId, String newDisplayName) {
    // In single-player, the "playerId" is the SP detective's ID.
    // The display name is usually managed by the GameClient directly for its prompts.
    // This context method is called if an UpdateDisplayNameCommand were to be executed.
    // For SP, this might just be a log or a no-op, as the client handles its own display.
    // Or, if the SP Detective object also stored a display name, you could update it here.

    Detective spDetective = getPlayerDetective(playerId); // Will return the single detective
    if (spDetective != null) {
      String oldName =
          spDetective
              .getPlayerId(); // Assuming getPlayerId() also acts as display name for SP detective
      // If Detective had a separate display name field, use that.

      // If the SP Detective object itself stores a display name that can be changed:
      // spDetective.setDisplayName(newDisplayName); // Assuming such a method exists

      logContextMessage(
          "Display name update processed for "
              + oldName
              + " to "
              + newDisplayName
              + ". (In SP, client usually handles its own display name for prompts).");

      // Send a confirmation back to the "player" (the console)
      sendResponseToPlayer(
          playerId, new TextMessage("Display name noted as: " + newDisplayName, false));
    } else {
      logContextMessage(
          "Error: processUpdateDisplayName called, but SP detective not found for ID: " + playerId);
    }
  }

  @Override
  public void processRequestInitiateExam(String requestingPlayerId) {
    // In single player, if the player types "request final exam",
    // it's equivalent to them directly initiating it.
    logContextMessage(
        "Received 'request final exam' in Single Player for player: " + requestingPlayerId);
    if (!isCaseStarted()) {
      // Use requestingPlayerId for the response
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("The case has not started yet. Cannot start exam.", true));
      return;
    }
    if (isExamActive) { // Check the internal SP flag
      // Use requestingPlayerId for the response
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("An exam is already in progress.", false));
      return;
    }
    // Directly proceed to start the exam.
    startExamProcess(requestingPlayerId); // Call the method that handles exam initiation
  }

  @Override
  public void processRequestStartCase(String requestingPlayerId) {
    // In single player, the player is effectively their own host.
    // If they type "request start case", it's like they are telling themselves to start.
    logContextMessage(
        "Received 'request start case' in Single Player for player: " + requestingPlayerId);
    if (isCaseStarted()) {
      // Use requestingPlayerId for the response
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("The case has already started.", false));
      return;
    }
    if (getSelectedCase() == null) {
      // Use requestingPlayerId for the response
      sendResponseToPlayer(requestingPlayerId, new TextMessage("No case selected to start.", true));
      return;
    }

    // Directly proceed to start the case logic.
    // The actual display of initial case info is handled by setCaseStarted ->
    // broadcastInitialCaseDetails (SP version)
    // which implicitly targets the single player.
    setCaseStarted(true);
    // If setCaseStarted doesn't automatically display all initial info, you might need to trigger
    // it here.
    // For example, if broadcastInitialCaseDetails was a separate public method:
    // broadcastInitialCaseDetails(requestingPlayerId); // if it took playerId
    // But our SP setCaseStarted already triggers the print via its own broadcast/print logic.
    // A simple confirmation can be sent if setCaseStarted doesn't give immediate feedback.
    // sendResponseToPlayer(requestingPlayerId, new TextMessage("Case is now starting...", false));
    // This is likely redundant if setCaseStarted -> broadcastInitialCaseDetails prints everything.
  }

  // Called AFTER extractors have populated rooms and suspects
  public void initializeNewCase(CaseFile caseFile, String startingRoomName) {
    this.selectedCase = caseFile;
    // Detective state reset is done in resetForNewCaseLoad

    if (caseFile.getTasks() != null) {
      this.taskList = new TaskList(new ArrayList<>(caseFile.getTasks()));
    } else {
      this.taskList = new TaskList(new ArrayList<>());
    }

    if (caseFile.getWatsonHints() != null && !caseFile.getWatsonHints().isEmpty()) {
      this.watson = new DoctorWatson(new ArrayList<>(caseFile.getWatsonHints()));
    } else {
      this.watson = new DoctorWatson(new ArrayList<>()); // Watson with no hints
    }

    // Set player and Watson's starting room
    Room startingRoom = getRoomByName(startingRoomName);
    if (startingRoom != null) {
      this.currentRoom = startingRoom;
      this.detective.setCurrentRoom(startingRoom);
      if (this.watson != null) {
        this.watson.setCurrentRoom(startingRoom);
      }
      logContextMessage(
          "Initialized case '"
              + caseFile.getTitle()
              + "'. Starting room: "
              + startingRoom.getName());
    } else {
      logContextMessage(
          "CRITICAL Error: Starting room '"
              + startingRoomName
              + "' not found! Cannot set initial position.");
      // Assign a fallback room if possible, otherwise the player starts nowhere.
      if (!this.rooms.isEmpty()) {
        this.currentRoom = this.rooms.values().iterator().next();
        this.detective.setCurrentRoom(this.currentRoom);
        if (this.watson != null) this.watson.setCurrentRoom(this.currentRoom);
        logContextMessage(
            "Warning: Using first available room '"
                + this.currentRoom.getName()
                + "' as fallback starting room.");
      } else {
        logContextMessage("CRITICAL Error: No rooms loaded at all. Player cannot be placed.");
      }
    }

    // Initialize suspect positions (if not already done by extractor with specific rules)
    // Only do random placement if suspect rooms aren't set
    if (!this.suspects.isEmpty()
        && this.suspects.stream().allMatch(s -> s.getCurrentRoom() == null)) {
      logContextMessage("Initializing random suspect positions...");
      if (!this.rooms.isEmpty()) {
        List<Room> allRoomsList = new ArrayList<>(this.rooms.values());
        for (Suspect suspect : this.suspects) {
          suspect.setCurrentRoom(allRoomsList.get(random.nextInt(allRoomsList.size())));
        }
      }
    } else {
      logContextMessage("Suspect positions assumed set by extractor or already initialized.");
    }
  }

  // Helper for internal logging
  private void logContextMessage(String message) {
    System.out.println("[SP_CONTEXT] " + message);
  }

  // --- GameContext Implementation (for Extractors) ---

  @Override
  public void addRoom(Room room) {
    if (room != null && room.getName() != null) {
      this.rooms.put(room.getName().toLowerCase(), room);
    } else {
      logContextMessage("Warning: Attempted to add null room or room with null name.");
    }
  }

  @Override
  public Room getRoomByName(String name) {
    if (name == null) return null;
    return this.rooms.get(name.toLowerCase());
  }

  @Override
  public Map<String, Room> getAllRooms() {
    return Collections.unmodifiableMap(this.rooms);
  }

  @Override
  public void addSuspect(Suspect suspect) {
    if (suspect != null) {
      this.suspects.add(suspect);
    } else {
      logContextMessage("Warning: Attempted to add null suspect.");
    }
  }

  @Override
  public void logLoadingMessage(String message) {
    System.out.println("[LOADER_SP] " + message);
  }

  @Override
  public String getContextIdForLog() {
    return "SinglePlayer";
  }

  // --- GameActionContext Implementation (for Commands) ---

  @Override
  public boolean isCaseStarted() {
    return this.caseStarted;
  }

  @Override
  public CaseFile getSelectedCase() {
    return this.selectedCase;
  }

  @Override
  public Detective getPlayerDetective(String playerId) {
    // In Single Player, playerId is ignored
    if (this.detective == null) {
      logContextMessage("Warning: getPlayerDetective called but detective object is null!");
    }
    return this.detective;
  }

  @Override
  public Room getCurrentRoomForPlayer(String playerId) {
    // In Single Player, playerId is ignored
    if (this.currentRoom == null) {
      // This can happen if initialization failed
      logContextMessage("Warning: getCurrentRoomForPlayer called but currentRoom is null!");
    }
    return this.currentRoom;
  }

  @Override
  public String getOccupantsDescriptionInRoom(Room room, String askingPlayerId) {
    if (room == null) {
      logContextMessage("Error: getOccupantsDescriptionInRoom called with null room.");
      return "Occupants: Error";
    }
    List<String> occupantNames = new ArrayList<>();

    // Suspects
    for (Suspect suspect : this.suspects) {
      if (suspect.getCurrentRoom() != null
          && suspect.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
        occupantNames.add(suspect.getName());
      }
    }

    // Watson
    if (this.watson != null
        && this.watson.getCurrentRoom() != null
        && this.watson.getCurrentRoom().getName().equalsIgnoreCase(room.getName())) {
      occupantNames.add("Dr. Watson");
    }

    if (occupantNames.isEmpty()) {
      return "Occupants: None";
    }
    return "Occupants: " + String.join(", ", occupantNames);
  }

  @Override
  public TaskList getTaskList() {
    return this.taskList;
  }

  @Override
  public DoctorWatson getWatson() {
    return this.watson;
  }

  @Override
  public List<Suspect> getAllSuspects() {
    return Collections.unmodifiableList(this.suspects);
  }

  // Inside GameContextSinglePlayer.java
  @Override
  public boolean movePlayer(String playerId, String direction) {
    if (this.currentRoom == null) { // Player's current room before the move
      sendResponseToPlayer(
          playerId, new TextMessage("Error: Cannot move, current location unknown.", true));
      return false;
    }

    Room oldPlayerRoom = this.currentRoom; // Capture the room player is LEAVING
    Room newPlayerRoom = oldPlayerRoom.getNeighbor(direction.toLowerCase());

    if (newPlayerRoom != null) {
      this.currentRoom = newPlayerRoom; // Update player's current room to the NEW one
      this.detective.setCurrentRoom(newPlayerRoom);

      updateNpcMovements(playerId); // New method name for clarity

      // Send updated room description (will now reflect NPCs' new positions)
      sendResponseToPlayer(playerId, createRoomDescriptionDTO(newPlayerRoom, playerId));

      return true;
    } else {
      sendResponseToPlayer(
          playerId,
          new TextMessage(
              "You can't move " + direction + " from " + oldPlayerRoom.getName() + ".", false));
      return false;
    }
  }

  @Override
  public void startExamProcess(String playerId) {
    // This method is called by InitiateFinalExamCommand
    this.startExamForPlayer(playerId); // Call the existing SP-specific method
  }

  // Helper to create RoomDescriptionDTO (used by move and potentially look command)
  private RoomDescriptionDTO createRoomDescriptionDTO(Room room, String playerId) {
    if (room == null) return null; // Or return an error DTO?
    List<String> objectNames =
        room.getObjects().values().stream().map(GameObject::getName).collect(Collectors.toList());
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

    return new RoomDescriptionDTO(
        room.getName(), room.getDescription(), objectNames, occupantNamesList, exits);
  }

  @Override
  public void addJournalEntry(JournalEntryDTO entry) {
    // *** IMPLEMENT THE LOGIC HERE ***
    if (entry == null) {
      logContextMessage("Attempted to add a null entry to the journal.");
      return;
    }

    boolean added = this.journal.addEntry(entry); // Call the addEntry method of the Journal object

    if (added) {
      logContextMessage(
          "Journal entry added by "
              + entry.getContributorPlayerId()
              + ". Journal size now: "
              + this.journal.getEntryCount());
    } else {
      // This log will now correctly reflect if the Journal's contains() check (based on text and
      // contributor)
      // found it to be a duplicate.
      logContextMessage(
          "Journal entry by "
              + entry.getContributorPlayerId()
              + " was considered a duplicate, not added. Journal size: "
              + this.journal.getEntryCount());
    }
    // The command (e.g., JournalAddCommand) is responsible for sending user-facing feedback like
    // "Note added to journal: ..." via context.sendResponseToPlayer().
    // This context method just handles the internal state update and internal logging.
  }

  @Override
  public List<JournalEntryDTO> getJournalEntries(String playerId) {
    // This method correctly gets entries from this.journal
    // The unmodifiableList from Core.Journal.getEntries() is good,
    // but if you want to ensure the caller gets a *new* list instance
    // that they can't even attempt to cast back and modify (very defensive),
    // then creating a new list from the unmodifiable one is fine.
    // However, Collections.unmodifiableList is generally sufficient protection.
    // For consistency with how it was, let's keep creating a new sorted list.
    List<JournalEntryDTO> entriesFromJournal = this.journal.getEntries(); // Gets unmodifiable list
    return new ArrayList<>(entriesFromJournal)
        .stream() // Create a new mutable list for sorting
            .sorted(Comparator.comparingLong(JournalEntryDTO::getTimestamp))
            .collect(Collectors.toList());
  }

  @Override
  public void sendResponseToPlayer(String playerId, Serializable responseDto) {
    // In Single Player, print to console.
    if (responseDto == null) return;

    String output;
    if (responseDto instanceof TextMessage) {
      output = ((TextMessage) responseDto).getText();
    } else if (responseDto instanceof RoomDescriptionDTO rd) {
      StringBuilder sb = new StringBuilder();
      sb.append("\n--- ").append(rd.getName()).append(" ---").append("\n");
      sb.append(rd.getDescription()).append("\n");
      sb.append("Objects: ")
          .append(rd.getObjectNames().isEmpty() ? "None" : String.join(", ", rd.getObjectNames()))
          .append("\n");
      sb.append("Occupants: ")
          .append(
              rd.getOccupantNames().isEmpty() ? "None" : String.join(", ", rd.getOccupantNames()))
          .append("\n");
      sb.append("Exits: ");
      if (rd.getExits().isEmpty()) {
        sb.append("None");
      } else {
        rd.getExits()
            .forEach((dir, roomName) -> sb.append(dir).append(" (").append(roomName).append("), "));
        if (!rd.getExits().isEmpty()) sb.setLength(sb.length() - 2);
      }
      output = sb.toString();
    } else if (responseDto instanceof JournalEntryDTO) {
      output = "Journal: " + responseDto; // Assumes JournalEntryDTO.toString() is good
    } else if (responseDto instanceof ExamQuestionDTO) {
      // Keep custom formatting for exam questions as it includes "Enter your answer:"
      output =
          "\n--- EXAM QUESTION "
              + ((ExamQuestionDTO) responseDto).getQuestionNumber()
              + " ---\n"
              + ((ExamQuestionDTO) responseDto).getQuestionText()
              + "\nEnter your answer:";
    } else if (responseDto instanceof ExamResultDTO) {
      // *** CORRECTED LINE: Use the DTO's own toString() method ***
      output = responseDto.toString();
      // *** This toString() method in ExamResultDTO already includes the review details ***
    }
    // Consider adding specific handlers for other DTOs if their default toString isn't ideal for
    // player output
    // e.g., TaskListDTO, AvailableCasesDTO, PublicGamesListDTO if they are ever sent via
    // sendResponseToPlayer in SP
    else {
      // Fallback for any other DTO types
      output = "[SP_RESPONSE] " + responseDto;
    }
    System.out.println(output);
  }

  @Override
  public void broadcastToSession(Serializable dto, String excludePlayerId) {
    sendResponseToPlayer(null, dto);
  }

  @Override
  public void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom) {
    // No action needed in SP.
  }

  @Override
  public boolean canStartFinalExam(String playerId) {
    return isCaseStarted() && !isExamActive;
  }

  // Called by InitiateFinalExamCommand
  public void startExamForPlayer(String playerId) {
    if (!canStartFinalExam(playerId)) {
      sendResponseToPlayer(playerId, new TextMessage("Cannot start the final exam now.", true));
      return;
    }
    if (this.selectedCase == null
        || this.selectedCase.getFinalExam() == null
        || this.selectedCase.getFinalExam().isEmpty()) {
      sendResponseToPlayer(
          playerId,
          new TextMessage("No final exam questions are configured for this case.", false));
      return;
    }

    this.isExamActive = true;
    this.currentExamQuestionsList = new ArrayList<>(this.selectedCase.getFinalExam());
    this.playerAnswersMap = new HashMap<>();
    this.currentQuestionIndex = 0;

    sendResponseToPlayer(playerId, new TextMessage("--- Final Exam Started ---", false));
    sendNextQuestionToPlayer(playerId);
  }

  private void sendNextQuestionToPlayer(String playerId) {
    if (!isExamActive) return;

    if (currentQuestionIndex < currentExamQuestionsList.size()) {
      CaseFile.ExamQuestion currentQ = currentExamQuestionsList.get(currentQuestionIndex);
      // Send the DTO with the 1-based question number
      sendResponseToPlayer(
          playerId, new ExamQuestionDTO(currentQuestionIndex + 1, currentQ.getQuestion()));
    } else {
      // All questions asked
      evaluateAndSendExamResults(playerId);
    }
  }

  // Called by SubmitExamAnswerCommand
  public void processExamAnswer(String playerId, int questionNumber, String answerText) {
    // Validate if we are expecting an answer for this specific question number
    if (!isAwaitingExamAnswer() || questionNumber != getAwaitingQuestionNumber()) {
      sendResponseToPlayer(
          playerId,
          new TextMessage(
              "Not currently awaiting an answer for question " + questionNumber + ".", true));
      // Resend the current expected question
      if (isAwaitingExamAnswer()) sendNextQuestionToPlayer(playerId);
      return;
    }

    playerAnswersMap.put(questionNumber, answerText); // Store 1-based question number
    currentQuestionIndex++; // Move to next question index

    sendNextQuestionToPlayer(playerId); // Send next or evaluate
  }

  // Inside singleplayer/GameContextSinglePlayer.java

  private void evaluateAndSendExamResults(String playerId) {
    if (currentExamQuestionsList == null || playerAnswersMap == null) {
      sendResponseToPlayer(
          playerId, new TextMessage("Error: Exam data missing for evaluation.", true));
      resetExamState();
      return;
    }

    int score = 0;
    List<String> answersToReviewDetails = new ArrayList<>(); // To store details of wrong answers

    for (int i = 0; i < currentExamQuestionsList.size(); i++) {
      CaseFile.ExamQuestion actualQuestion = currentExamQuestionsList.get(i);
      String playerAnswer = playerAnswersMap.get(i + 1); // Answers stored with 1-based key

      if (playerAnswer != null && playerAnswer.equalsIgnoreCase(actualQuestion.getAnswer())) {
        score++;
      } else {
        // Question was answered incorrectly or not answered.
        // *** ENSURE THIS LINE DOES NOT INCLUDE actualQuestion.getAnswer() ***
        String reviewDetail =
            String.format(
                "Q%d: %s\n   Your Answer: '%s'", // NO CORRECT ANSWER HERE
                (i + 1),
                actualQuestion.getQuestion(),
                (playerAnswer != null ? playerAnswer : "No answer provided"));
        answersToReviewDetails.add(reviewDetail);
      }
    }

    this.detective.setFinalExamScore(score);
    this.detective.evaluateRank();

    String feedbackMessage;
    int totalQuestions = currentExamQuestionsList.size();
    if (score == totalQuestions) {
      feedbackMessage =
          "Outstanding! You've answered all questions correctly and solved the case perfectly.";
    } else if (score > 0) {
      feedbackMessage =
          "You've made some progress. Review your notes and the evidence for the questions you missed.";
    } else {
      feedbackMessage =
          "Unfortunately, your investigation fell short. Crucial details were missed. Review your notes and the evidence thoroughly.";
    }

    ExamResultDTO resultDTO =
        new ExamResultDTO(
            score,
            totalQuestions,
            feedbackMessage,
            this.detective.getRank(),
            answersToReviewDetails // Pass the list containing only question and player's answer
            );

    sendResponseToPlayer(playerId, resultDTO);
    sendResponseToPlayer(playerId, new TextMessage("--- Final Exam Concluded ---", false));

    resetExamState();
  }

  @Override
  public String askWatsonForHint(String playerId) {
    if (this.watson == null) return "Dr. Watson is not available in this case.";
    if (this.currentRoom == null) return "Your location is unknown.";
    if (this.watson.getCurrentRoom() == null) return "Dr. Watson's location is unknown.";

    if (this.watson.getCurrentRoom().getName().equalsIgnoreCase(this.currentRoom.getName())) {
      String hint = this.watson.provideHint(); // Get hint from Watson object
      if (hint == null || hint.trim().isEmpty()) {
        hint = "Dr. Watson seems to have no particular insight at the moment.";
      }
      // NO addJournalEntry, NO sendResponseToPlayer here
      return hint; // Return just the hint text
    }
    return "Dr. Watson is not here to offer a hint."; // Indicate Watson not present
  }

  @Override
  public void updateNpcMovements(String triggeringPlayerId) {
    if (!caseStarted) {
      // logContextMessage("NPC Movement SKIPPED: Case not started.");
      return;
    }
    // this.currentRoom here IS the player's NEW room.
    // For this logic, we don't need to reference it for suspect movement decisions.
    if (this.currentRoom == null) {
      // logContextMessage("NPC Movement SKIPPED: Player's current room is unknown (should not
      // happen if case started).");
      return;
    }
    // logContextMessage("NPC Movement START. Player is in: " + playerCurrentRoomName);

    // --- Move Suspects (Completely Random to any Neighbor) ---
    for (Suspect suspect : this.suspects) {
      if (suspect.getCurrentRoom() == null) {
        // logContextMessage("Suspect " + suspect.getName() + " is not in any room, cannot move.");
        continue;
      }
      Map<String, Room> neighbors = suspect.getCurrentRoom().getNeighbors();

      if (neighbors.isEmpty()) {
        // logContextMessage("Suspect " + suspect.getName() + " in " + oldSuspectRoomName + " has no
        // neighbors, stays put.");
        continue;
      }

      // Suspect considers ALL neighbors as valid moves.
      List<Room> allPossibleMoves = new ArrayList<>(neighbors.values());

      if (!allPossibleMoves.isEmpty()) { // Should always be true if neighbors wasn't empty
        Room nextRoomForSuspect = allPossibleMoves.get(random.nextInt(allPossibleMoves.size()));
        suspect.setCurrentRoom(nextRoomForSuspect);
        // logContextMessage("Suspect " + suspect.getName() + " moved RANDOMLY from " +
        // oldSuspectRoomName + " to " + nextRoomForSuspect.getName() + ".");
      }
    }

    // --- Move Watson (logic remains the same, can always enter player room, moves randomly) ---
    if (this.watson != null && this.watson.getCurrentRoom() != null) {
      Map<String, Room> watsonNeighbors = this.watson.getCurrentRoom().getNeighbors();

      if (!watsonNeighbors.isEmpty()) {
        List<Room> watsonPossibleMoves = new ArrayList<>(watsonNeighbors.values());
        Room nextRoomForWatson =
            watsonPossibleMoves.get(random.nextInt(watsonPossibleMoves.size()));
        this.watson.setCurrentRoom(nextRoomForWatson);
        // logContextMessage("Dr. Watson moved RANDOMLY from " + oldWatsonRoomName + " to " +
        // nextRoomForWatson.getName() + ".");
      }
    }
    // logContextMessage("NPC Movement END. Player remains in: " + playerCurrentRoomName);
  }

  @Override
  public void handlePlayerExitRequest(String playerId) {
    if (isCaseStarted() || isExamActive) {
      sendResponseToPlayer(
          playerId, new TextMessage("Exiting current case. Returning to case selection.", false));
      resetExamState();
      this.caseStarted = false;
      this.wantsToExitToCaseSelection = true;
    } else {
      sendResponseToPlayer(playerId, new TextMessage("Exiting application.", false));
      this.wantsToExitApplication = true;
    }
  }

  // --- SP Specific Flow Control ---
  public boolean wantsToExitToCaseSelection() {
    return wantsToExitToCaseSelection;
  }

  public boolean wantsToExitApplication() {
    return wantsToExitApplication;
  }

  public void resetExitFlags() {
    this.wantsToExitApplication = false;
    this.wantsToExitToCaseSelection = false;
  }

  // --- Getters/Setters used by SinglePlayerMain ---
  public boolean isAwaitingExamAnswer() {
    return isExamActive
        && currentExamQuestionsList != null
        && currentQuestionIndex < currentExamQuestionsList.size();
  }

  public int getAwaitingQuestionNumber() {
    return isAwaitingExamAnswer() ? currentQuestionIndex + 1 : 0;
  }
}
