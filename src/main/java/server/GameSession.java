package server;

import JsonDTO.CaseFile;
import common.commands.*;
import common.dto.*;
import extractors.BuildingExtractor;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GameSession Manages a single instance of a multiplayer game (typically for 2 players). It holds
 * references to the connected ClientSessions, the specific CaseFile being played, and the
 * GameContextServer which contains the live game state. Handles player joining, leaving, command
 * processing for the session, and chat.
 */
public class GameSession {

  // --- Core Session Fields ---
  private final String sessionId; // Unique ID for this game session.
  private final String caseTitle; // Title of the case, from CaseFile.
  private final CaseFile caseFile; // The actual case data DTO.
  private final GameContextServer gameContext; // Authoritative game state and logic engine.

  private ClientSession player1; // Host player.
  private ClientSession player2; // Guest player.
  private final ReentrantLock sessionLock =
      new ReentrantLock(); // For thread-safe access to session state.

  private GameSessionState
      state; // Current state of the session (LOADING, WAITING, ACTIVE, ENDED, etc.)
  private String gameCode; // 5-char code for private games, null if public.

  // --- Manager & Server References ---
  private final GameSessionManager sessionManager; // To notify about ending, or for save requests.
  private final GameServer server; // Primarily for logging.

  /**
   * Constructor for a new GameSession. Initializes session ID, case data, host player, and game
   * context. Attempts to load all case data into the context.
   */
  public GameSession(
      String caseTitle,
      CaseFile caseFile,
      ClientSession hostPlayer,
      boolean isPublic,
      GameSessionManager manager,
      GameServer server) {
    this.sessionId = UUID.randomUUID().toString();
    this.caseTitle = Objects.requireNonNull(caseTitle, "Case title cannot be null");
    this.caseFile = Objects.requireNonNull(caseFile, "CaseFile cannot be null");
    this.sessionManager = Objects.requireNonNull(manager, "GameSessionManager cannot be null");
    this.server = Objects.requireNonNull(server, "GameServer cannot be null");
    this.state = GameSessionState.LOADING; // Initial state while setting up.

    this.player1 = Objects.requireNonNull(hostPlayer, "Host player (player1) cannot be null");
    hostPlayer.setAssociatedGameSession(this); // Link client to this session.

    if (!isPublic) {
      this.gameCode = generateGameCode();
      log("Private game session created. Code: " + this.gameCode);
    } else {
      log("Public game session created.");
    }

    // Create the game logic context for this session. Player 2 ID is initially null.
    this.gameContext = new GameContextServer(this, this.caseFile, hostPlayer.getPlayerId(), null);

    if (!loadCaseDataIntoContext()) {
      this.state = GameSessionState.ERROR; // Mark session as errored if loading fails.
      log(
          "CRITICAL: Failed to load case data for new session "
              + sessionId
              + ". Session state: ERROR.");
      hostPlayer.send(
          new TextMessage(
              "Error: Failed to initialize the game data for this case. Session cannot start.",
              true));
      // The GameSessionManager should ideally detect this ERROR state and not list it, or clean it
      // up.
    } else {
      this.state = GameSessionState.WAITING_FOR_PLAYERS; // Ready for player 2.
      log(
          "Session created for case '"
              + this.caseTitle
              + "'. Host: "
              + hostPlayer.getDisplayId()
              + ". Waiting for Player 2.");
      hostPlayer.send(
          new HostGameResponseDTO(
              true,
              "Game hosted. Waiting for opponent..."
                  + (isPublic ? "" : " Private Code: " + this.gameCode),
              this.gameCode,
              this.sessionId));
    }
  }

  // --- Initialization & Setup ---

  /**
   * Loads all game data (rooms, objects, suspects) from the CaseFile into this session's
   * GameContextServer. Called during session construction.
   *
   * @return true if loading was successful, false otherwise.
   */
  private boolean loadCaseDataIntoContext() {
    log("Loading case data into context...");
    gameContext.resetForNewCaseLoad(); // Start fresh.

    try {
      if (!BuildingExtractor.loadBuilding(this.caseFile, this.gameContext)) {
        log("Failed to load building data.");
        return false;
      }
      GameObjectExtractor.loadObjects(this.caseFile, this.gameContext);
      SuspectExtractor.loadSuspects(
          this.caseFile, this.gameContext); // Can throw IllegalStateException.
    } catch (IllegalStateException e) {
      log("Error loading suspects: " + e.getMessage());
      return false;
    } catch (Exception e) {
      log("Unexpected error during case data loading: " + e.getMessage());
      server.logError(
          "Stack trace for unexpected loading error in session " + sessionId, e); // Log full trace.
      return false;
    }
    gameContext.initializePlayerStartingState(); // Set initial positions for players, NPCs.
    log("Case data loaded successfully.");
    return true;
  }

  /** Generates a simple alphanumeric code for private games. */
  private String generateGameCode() {
    String chars = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789"; // Omitted 'O'
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 5; i++) {
      code.append(chars.charAt((int) (Math.random() * chars.length())));
    }
    return code.toString();
  }

  // --- Getters ---
  public String getSessionId() {
    return sessionId;
  }

  public String getCaseTitle() {
    return caseTitle;
  }

  public String getGameCode() {
    return gameCode;
  } // Null for public games.

  public GameSessionState getState() {
    return state;
  }

  public void setSessionState(GameSessionState newState) { // Used by GameContextServer
    sessionLock.lock();
    try {
      if (this.state != newState) {
        log("Session state changing from " + this.state + " to " + newState);
        this.state = newState;
      }
    } finally {
      sessionLock.unlock();
    }
  }

  public GameContextServer getGameContext() {
    return gameContext;
  }

  public GameServer getServer() {
    return server;
  } // For GameContextServer to log through.

  public ClientSession getPlayer1() {
    sessionLock.lock();
    try {
      return player1;
    } finally {
      sessionLock.unlock();
    }
  }

  // public ClientSession getPlayer2() { sessionLock.lock(); try { return player2; } finally {
  // sessionLock.unlock(); } } // If needed publicly

  // --- Player Management ---

  /**
   * Adds a second player (guest) to this game session.
   *
   * @param newPlayer The ClientSession of the player joining.
   * @return true if player was successfully added, false otherwise (e.g., session full, wrong
   *     state).
   */
  public boolean addPlayer(ClientSession newPlayer) {
    if (newPlayer == null) {
      log("Attempt to add null player.");
      return false;
    }
    sessionLock.lock();
    try {
      if (player2 != null) {
        log(
            "Add player "
                + newPlayer.getDisplayId()
                + " failed: P2 slot full (current P2: "
                + player2.getDisplayId()
                + ").");
        newPlayer.send(new JoinGameResponseDTO(false, "Session is already full.", null));
        return false;
      }
      if (this.state != GameSessionState.WAITING_FOR_PLAYERS) {
        log(
            "Add player "
                + newPlayer.getDisplayId()
                + " failed: Session not WAITING_FOR_PLAYERS. State: "
                + this.state);
        newPlayer.send(
            new JoinGameResponseDTO(false, "Session not currently accepting new players.", null));
        return false;
      }
      if (player1 != null && player1.getPlayerId().equals(newPlayer.getPlayerId())) {
        log(
            "Add player "
                + newPlayer.getDisplayId()
                + " failed: Host cannot join own session as P2.");
        newPlayer.send(
            new JoinGameResponseDTO(
                false, "You cannot join your own game as the second player.", null));
        return false;
      }

      player2 = newPlayer;
      newPlayer.setAssociatedGameSession(this);
      log("Player 2 (" + player2.getDisplayId() + ") joined session.");

      // Update GameContextServer with both player IDs and re-init their states.
      this.gameContext.setPlayerIds(player1.getPlayerId(), player2.getPlayerId());
      this.gameContext.initializePlayerStartingState(); // Ensure both players are properly placed.

      newPlayer.send(
          new JoinGameResponseDTO(
              true,
              "Joined game: " + caseTitle + " with host " + player1.getDisplayId(),
              this.sessionId));
      if (player1 != null) { // Notify host.
        player1.send(
            new LobbyUpdateDTO(
                newPlayer.getDisplayId() + " has joined your game!",
                getPlayerDisplayIds(),
                getPlayerActualIds(),
                player1.getPlayerId(),
                false));
      }
      startGameSession(); // Transition session to ready for 'start case'.
      return true;
    } finally {
      sessionLock.unlock();
    }
  }

  /**
   * Transitions the session state when both players have joined. Sends lobby updates and case
   * invitation.
   */
  private void startGameSession() {
    // Pre-condition: P2 has just joined, session is full.
    if (!isFull() || this.state != GameSessionState.WAITING_FOR_PLAYERS) {
      log("startGameSession called prematurely. State: " + this.state + ", Full: " + isFull());
      return;
    }
    setSessionState(GameSessionState.IN_LOBBY_AWAITING_START); // Now ready for 'start case'.
    log(
        "Session is now "
            + this.state
            + ". Players: "
            + player1.getDisplayId()
            + ", "
            + player2.getDisplayId());

    LobbyUpdateDTO gameReadyMsg =
        new LobbyUpdateDTO(
            "Both players are in the lobby: "
                + player1.getDisplayId()
                + " and "
                + player2.getDisplayId()
                + ".",
            getPlayerDisplayIds(),
            getPlayerActualIds(),
            (player1 != null ? player1.getPlayerId() : null),
            true);
    broadcast(gameReadyMsg, null);

    broadcast(
        new TextMessage(
            "--- Case Invitation ---\n"
                + caseFile.getInvitation()
                + "\n\nHost ("
                + player1.getDisplayId()
                + ") should type 'start case' to begin.",
            false),
        null);
  }

  public boolean isFull() {
    return player1 != null && player2 != null;
  }

  public List<String> getPlayerDisplayIds() {
    /* ... (as before) ... */
    List<String> ids = new ArrayList<>();
    if (player1 != null) ids.add(player1.getDisplayId());
    if (player2 != null) ids.add(player2.getDisplayId());
    return ids;
  }

  private List<String> getPlayerActualIds() {
    /* ... (as before) ... */
    List<String> ids = new ArrayList<>();
    if (player1 != null) ids.add(player1.getPlayerId());
    if (player2 != null) ids.add(player2.getPlayerId());
    return ids;
  }

  /**
   * Handles a client disconnecting from the server (e.g., socket closed). Called by
   * GameSessionManager, which is called by GameServer.
   */
  public void handlePlayerDisconnect(ClientSession disconnectedClient) {
    /* ... (as before, ensure LobbyUpdateDTO is correct) ... */
    if (disconnectedClient == null) return;
    sessionLock.lock();
    try {
      String leavingPlayerDisplayId = disconnectedClient.getDisplayId();
      String leavingPlayerId = disconnectedClient.getPlayerId();
      boolean wasP1 = player1 != null && player1.getPlayerId().equals(leavingPlayerId);
      boolean wasP2 = player2 != null && player2.getPlayerId().equals(leavingPlayerId);

      if (!wasP1 && !wasP2) return; // Not in this session.

      log("Player " + leavingPlayerDisplayId + " (ID: " + leavingPlayerId + ") disconnected.");
      disconnectedClient.setAssociatedGameSession(null);

      if (wasP1) player1 = null;
      if (wasP2) player2 = null;
      gameContext.setPlayerIds(
          player1 != null ? player1.getPlayerId() : null,
          player2 != null ? player2.getPlayerId() : null);

      if (this.state == GameSessionState.ACTIVE
          || this.state == GameSessionState.WAITING_FOR_PLAYERS
          || this.state == GameSessionState.IN_LOBBY_AWAITING_START) {
        this.state = GameSessionState.ENDED_ABANDONED;
        ClientSession remainingPlayer = (player1 != null) ? player1 : player2;
        if (remainingPlayer != null) {
          remainingPlayer.send(
              new LobbyUpdateDTO(
                  leavingPlayerDisplayId + " left. Session ended.",
                  getPlayerDisplayIds(),
                  getPlayerActualIds(),
                  (player1 != null ? player1.getPlayerId() : null),
                  false));
          remainingPlayer.send(new ReturnToLobbyDTO("Opponent disconnected. Session ended."));
          remainingPlayer.setAssociatedGameSession(null);
        }
        sessionManager.endSession(
            this.sessionId, "Player " + leavingPlayerDisplayId + " disconnected.");
      } else {
        sessionManager.endSession(
            this.sessionId, "Player disconnected during state: " + this.state);
      }
    } finally {
      sessionLock.unlock();
    }
  }

  /**
   * Handles a player gracefully exiting via the 'exit' command. Called by GameContextServer when an
   * ExitCommand is processed.
   */
  public void playerRequestsExit(String playerId) {
    /* ... (as before, ensure LobbyUpdateDTO is correct) ... */
    sessionLock.lock();
    try {
      ClientSession exitingPlayer = getClientSessionById(playerId);
      if (exitingPlayer == null) {
        return;
      }
      log("Player " + exitingPlayer.getDisplayId() + " requests to exit.");

      this.state = GameSessionState.ENDED_ABANDONED;
      exitingPlayer.send(new TextMessage("You have left the game session.", false));
      exitingPlayer.send(new ReturnToLobbyDTO("You have exited the game."));
      exitingPlayer.setAssociatedGameSession(null);

      ClientSession otherPlayer = getOtherPlayer(playerId);
      if (otherPlayer != null) {
        List<String> displayIdsForOther = new ArrayList<>();
        displayIdsForOther.add(otherPlayer.getDisplayId());
        List<String> actualIdsForOther = new ArrayList<>();
        actualIdsForOther.add(otherPlayer.getPlayerId());
        String hostIdForOther =
            (player1 == otherPlayer)
                ? otherPlayer.getPlayerId()
                : ((player2 == otherPlayer)
                    ? null
                    : this.player1 != null ? this.player1.getPlayerId() : null);

        otherPlayer.send(
            new LobbyUpdateDTO(
                exitingPlayer.getDisplayId() + " exited. Session ended.",
                displayIdsForOther,
                actualIdsForOther,
                hostIdForOther,
                false));
        otherPlayer.send(
            new ReturnToLobbyDTO(exitingPlayer.getDisplayId() + " exited. Session ended."));
        otherPlayer.setAssociatedGameSession(null);
      }

      if (player1 == exitingPlayer) player1 = null;
      if (player2 == exitingPlayer) player2 = null;
      gameContext.setPlayerIds(
          player1 != null ? player1.getPlayerId() : null,
          player2 != null ? player2.getPlayerId() : null);

      sessionManager.endSession(
          this.sessionId, "Player " + exitingPlayer.getDisplayId() + " exited.");
    } finally {
      sessionLock.unlock();
    }
  }

  // --- Command & Message Processing ---

  /**
   * Processes a command received from a client in this session. Gates commands based on current
   * session state. Delegates execution to GameContextServer.
   */
  public void processCommand(Command command, String playerId) {
    /* ... (as provided, with refined state checks) ... */
    if (command == null || playerId == null) {
      log("Error: Null command or playerId in processCommand");
      return;
    }
    sessionLock.lock();
    try {
      command.setPlayerId(playerId);
      boolean allowed = false;
      String cmdName = command.getClass().getSimpleName().replace("Command", "");
      log("Attempting command " + cmdName + " for " + playerId + " in state " + this.state);

      if (this.state == GameSessionState.ACTIVE) {
        allowed = true;
      } else if (this.state == GameSessionState.IN_LOBBY_AWAITING_START) {
        if (command instanceof StartCaseCommand
            || command instanceof RequestStartCaseCommand
            || command instanceof ExitCommand
            || command instanceof HelpCommand) {
          allowed = true;
        }
      } // Removed: WAITING_FOR_PLAYERS && isFull() because IN_LOBBY_AWAITING_START covers this.

      if (!allowed) {
        ClientSession sender = getClientSessionById(playerId);
        String msg = "Command '" + cmdName + "' not allowed in session state: " + this.state;
        if (sender != null) sender.send(new TextMessage(msg, true));
        log(msg + " (Player: " + playerId + ")");
        return;
      }
      log("Command " + cmdName + " allowed. Executing via context...");
      gameContext.executeCommand(command);
    } finally {
      sessionLock.unlock();
    }
  }

  /** Processes a chat message, broadcasting it to session members. */
  public void processChatMessage(ChatMessage chatMessage) {
    /* ... (as before) ... */
    if (chatMessage == null) return;
    sessionLock.lock();
    try {
      // Allow chat if session is WAITING (and full), IN_LOBBY_AWAITING_START, or ACTIVE
      if (this.state == GameSessionState.ACTIVE
          || this.state == GameSessionState.IN_LOBBY_AWAITING_START
          || (this.state == GameSessionState.WAITING_FOR_PLAYERS && isFull())) {
        log("Chat: <" + chatMessage.getSenderDisplayId() + "> " + chatMessage.getText());
        broadcast(chatMessage, null); // Send to all, including sender.
      } else {
        ClientSession sender = getClientSessionByDisplayId(chatMessage.getSenderDisplayId());
        if (sender != null)
          sender.send(
              new TextMessage("Chat not available in current session state: " + this.state, true));
      }
    } finally {
      sessionLock.unlock();
    }
  }

  /** Broadcasts a Serializable DTO to players in this session. */
  public void broadcast(Serializable dto, String excludePlayerId) {
    /* ... (as before) ... */
    sessionLock.lock();
    try {
      if (player1 != null && (!player1.getPlayerId().equals(excludePlayerId))) {
        player1.send(dto);
      }
      if (player2 != null && (!player2.getPlayerId().equals(excludePlayerId))) {
        player2.send(dto);
      }
    } finally {
      sessionLock.unlock();
    }
  }

  // --- Utility & Helper Methods ---
  public ClientSession getClientSessionById(String playerId) {
    /* ... (as before) ... */
    if (playerId == null) return null;
    sessionLock.lock(); // Lock when accessing shared player1/player2
    try {
      if (player1 != null && player1.getPlayerId().equals(playerId)) return player1;
      if (player2 != null && player2.getPlayerId().equals(playerId)) return player2;
      return null;
    } finally {
      sessionLock.unlock();
    }
  }

  private ClientSession getClientSessionByDisplayId(String displayId) {
    /* ... (as before) ... */
    if (displayId == null) return null;
    sessionLock.lock();
    try {
      if (player1 != null && player1.getDisplayId().equals(displayId)) return player1;
      if (player2 != null && player2.getDisplayId().equals(displayId)) return player2;
      return null;
    } finally {
      sessionLock.unlock();
    }
  }

  public ClientSession getOtherPlayer(String currentPlayerId) {
    /* ... (as before) ... */
    if (currentPlayerId == null) return null;
    sessionLock.lock();
    try {
      if (player1 != null && player1.getPlayerId().equals(currentPlayerId))
        return player2; // Can be null
      if (player2 != null && player2.getPlayerId().equals(currentPlayerId))
        return player1; // Can be null
      return null;
    } finally {
      sessionLock.unlock();
    }
  }

  /**
   * Finalizes the session, sends end messages, and prepares for removal by the manager. Called by
   * GameSessionManager or internally (e.g. player disconnect/exit).
   */
  public void endSession(String reason) {
    /* ... (as before, ensure LobbyUpdateDTO is correct) ... */
    sessionLock.lock();
    try {
      if (this.state == GameSessionState.ENDED_ABANDONED
          || this.state == GameSessionState.ENDED_NORMAL
          || this.state == GameSessionState.ERROR) {
        return; // Already ended.
      }
      GameSessionState prevState = this.state;
      this.state = GameSessionState.ENDED_NORMAL; // Default unless specified
      if (reason.toLowerCase().contains("abandoned")
          || reason.toLowerCase().contains("disconnect")
          || reason.toLowerCase().contains("exit")) {
        this.state = GameSessionState.ENDED_ABANDONED;
      }
      log("Session transitioning from " + prevState + " to " + this.state + ". Reason: " + reason);

      List<String> displayIds = getPlayerDisplayIds();
      List<String> actualIds = getPlayerActualIds();
      String hostId =
          (player1 != null && player1.getPlayerId() != null)
              ? player1.getPlayerId()
              : // If P1 was host
              (player2 != null
                      && player2.getPlayerId() != null
                      && actualIds.contains(player2.getPlayerId())
                  ? player2.getPlayerId()
                  : null); // Fallback if P1 left and P2 was considered host

      LobbyUpdateDTO endMsg =
          new LobbyUpdateDTO(
              "Session '" + caseTitle + "' ended: " + reason, displayIds, actualIds, hostId, false);
      broadcast(endMsg, null); // Notify any remaining connected clients.

      // Clear associations
      if (player1 != null) {
        player1.setAssociatedGameSession(null);
        player1 = null;
      }
      if (player2 != null) {
        player2.setAssociatedGameSession(null);
        player2 = null;
      }
      gameContext.setPlayerIds(null, null); // Inform context players are gone.
    } finally {
      sessionLock.unlock();
    }
  }

  /**
   * If the player whose name changed is the host (player1) of this session, and this session is
   * currently a public lobby, this method notifies the GameSessionManager to update its public
   * listing information with the new host display name. Called by GameContextServer after a
   * successful name change.
   *
   * @param updatedPlayerId The ID of the player whose name was just updated.
   * @param newDisplayName The new display name of that player.
   */
  public void notifyNameChangeToManagerIfHost(String updatedPlayerId, String newDisplayName) {
    sessionLock.lock();
    try {
      // Check if the updated player is indeed player1 (our designated host)
      // and if the session manager reference is valid.
      if (player1 != null
          && player1.getPlayerId().equals(updatedPlayerId)
          && sessionManager != null) {
        // Also, only bother updating the manager if this session *was* a public lobby.
        // A private game's host name change doesn't affect public listings.
        // We can check if gameCode is null (meaning it's public).
        if (this.gameCode == null) { // It's a public game
          // And it should ideally still be in a state where it *could* be listed
          // (e.g., WAITING_FOR_PLAYERS). If it's already ACTIVE, the lobby listing is gone.
          // However, GameSessionManager.updatePublicGameHostName can handle checking if it's still
          // in its publicLobbiesById map.
          log(
              "Host "
                  + updatedPlayerId
                  + " changed name to "
                  + newDisplayName
                  + ". Notifying manager to update public lobby info if applicable.");
          sessionManager.updatePublicGameHostName(this.sessionId, newDisplayName);
        }
      }
    } finally {
      sessionLock.unlock();
    }
  }

  /** Internal logging helper, prefixes with session ID. */
  private void log(String message) {
    String substring = sessionId.substring(0, Math.min(8, sessionId.length()));
    if (this.server != null) {
      this.server.log("[SESS:" + substring + "] " + message);
    } else {
      System.out.println("[SESS_NO_SERVER_LOG:" + substring + "] " + message);
    }
  }
}
