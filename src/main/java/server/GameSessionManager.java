package server;

import JsonDTO.CaseFile;
import common.commands.*;
import common.dto.*;
import extractors.CaseLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * GameSessionManager This is the big boss for all game sessions. It knows about available cases,
 * active games, public lobbies, and private game codes. Handles creating games, players joining,
 * and cleaning up sessions. Also processes "lobby commands" from clients not yet in a specific
 * game.
 */
public class GameSessionManager {

  // --- Fields ---
  // Cases available on the server, loaded from disk. Title (lowercase) -> CaseFile.
  private final Map<String, CaseFile> availableCases;
  // All currently active or loading game sessions. SessionID -> GameSession.
  private final Map<String, GameSession> activeSessionsById;
  // Public games that are waiting for a second player. SessionID -> GameSession.
  private final Map<String, GameSession> publicLobbiesById;
  // Mapping for private game codes to their session IDs. GameCode -> SessionID.
  private final Map<String, String> privateGameCodeToSessionId;

  // Lock to protect concurrent access to the session maps.
  private final ReentrantLock managerLock = new ReentrantLock();
  private final GameServer server; // For logging.
  private final PersistenceManager persistenceManager; // For saving/loading game states.

  private static final String CASES_DIRECTORY = "cases"; // Where my case JSONs live.

  // --- Constructor & Initialization ---
  public GameSessionManager(GameServer server, PersistenceManager persistenceManager) {
    this.server = server;
    this.persistenceManager = persistenceManager;
    this.availableCases = new ConcurrentHashMap<>();
    this.activeSessionsById = new ConcurrentHashMap<>();
    this.publicLobbiesById = new ConcurrentHashMap<>();
    this.privateGameCodeToSessionId = new ConcurrentHashMap<>();
    loadAllAvailableCases(); // Load 'em up when manager starts.
  }

  private void loadAllAvailableCases() {
    // My utility to read all JSONs from the cases' directory.
    List<CaseFile> cases = CaseLoader.loadCases(CASES_DIRECTORY);
    managerLock.lock(); // Protect availableCases map during modification
    try {
      availableCases.clear(); // Clear before reloading
      for (CaseFile cf : cases) {
        availableCases.put(cf.getTitle().toLowerCase(), cf);
      }
    } finally {
      managerLock.unlock();
    }
    server.log("Loaded " + availableCases.size() + " cases from '" + CASES_DIRECTORY + "'.");
  }

  /** Admin command: Reloads case files from disk. */
  public void reloadCases() {
    server.log("Admin: Reloading all case files...");
    loadAllAvailableCases(); // Just re-run the loading process.
  }

  // --- Public Game Listing & Case Info ---

  /** Provides DTOs for all cases server knows about (for client to pick when hosting). */
  public List<CaseInfoDTO> getAvailableCaseInfoDTOs() {
    // No lock needed for read if using ConcurrentHashMap or if availableCases is populated once.
    // If it can be modified by reloadCases concurrently, then lock or use CHM.
    // For now, assuming reloadCases is infrequent or handled carefully.
    return availableCases.values().stream()
        .map(cf -> new CaseInfoDTO(cf.getTitle(), cf.getDescription(), cf.getInvitation()))
        .collect(Collectors.toList());
  }

  /** Gets info for all current public lobbies waiting for players. */
  public List<PublicGameInfoDTO> getPublicLobbiesInfo() {
    managerLock.lock(); // Protect publicLobbiesById during iteration.
    try {
      return publicLobbiesById.values().stream()
          // Sanity checks: must be waiting and actually have a host.
          .filter(
              session ->
                  session.getState() == GameSessionState.WAITING_FOR_PLAYERS
                      && session.getPlayer1() != null
                      && session.getPlayerDisplayIds().size() == 1) // Only host present.
          .map(
              session -> {
                // Get current display ID of the host from their ClientSession.
                ClientSession hostClientSession = session.getPlayer1();
                String hostDisplayName =
                    (hostClientSession != null) ? hostClientSession.getDisplayId() : "Unknown Host";
                return new PublicGameInfoDTO(
                    hostDisplayName, session.getCaseTitle(), session.getSessionId());
              })
          .collect(Collectors.toList());
    } finally {
      managerLock.unlock();
    }
  }

  // --- Game Creation & Joining ---

  /** Creates a new game session (public or private). */
  public HostGameResponseDTO createGame(
      ClientSession hostClient, String caseTitle, boolean isPublic) {
    managerLock.lock(); // Protect session maps and hostClient's session association.
    try {
      CaseFile selectedCaseFile = availableCases.get(caseTitle.toLowerCase());
      if (selectedCaseFile == null) {
        return new HostGameResponseDTO(
            false, "Case '" + caseTitle + "' not found on server.", null, null);
      }
      if (hostClient.getAssociatedGameSession() != null) {
        // Host is already in a game/lobby.
        return new HostGameResponseDTO(
            false,
            "You are already in a game or lobby.",
            null,
            hostClient.getAssociatedGameSession().getSessionId());
      }

      GameSession newSession =
          new GameSession(
              selectedCaseFile.getTitle(), selectedCaseFile, hostClient, isPublic, this, server);
      if (newSession.getState() == GameSessionState.ERROR) {
        server.log("ERROR: GameSession constructor failed to load case data for " + caseTitle);
        return new HostGameResponseDTO(
            false,
            "Server error: Failed to initialize game session for case '" + caseTitle + "'.",
            null,
            null);
      }

      activeSessionsById.put(newSession.getSessionId(), newSession);
      if (isPublic) {
        publicLobbiesById.put(newSession.getSessionId(), newSession);
      } else { // Private game
        privateGameCodeToSessionId.put(newSession.getGameCode(), newSession.getSessionId());
      }
      server.log(
          "New game session created: "
              + newSession.getSessionId()
              + (isPublic ? " (Public)" : " (Private Code: " + newSession.getGameCode() + ")")
              + " for case: "
              + caseTitle
              + " by host: "
              + hostClient.getDisplayId());
      return new HostGameResponseDTO(
          true,
          "Game hosted. Waiting for opponent..."
              + (isPublic ? "" : " Code: " + newSession.getGameCode()),
          newSession.getGameCode(),
          newSession.getSessionId());
    } finally {
      managerLock.unlock();
    }
  }

  /** Allows a client to join an existing public game. */
  public JoinGameResponseDTO joinPublicGame(ClientSession joiningClient, String sessionId) {
    managerLock.lock();
    try {
      GameSession sessionToJoin = publicLobbiesById.get(sessionId);
      if (sessionToJoin == null
          || sessionToJoin.getState() != GameSessionState.WAITING_FOR_PLAYERS
          || sessionToJoin.isFull()) {
        return new JoinGameResponseDTO(
            false,
            "Public game (ID: "
                + sessionId.substring(0, Math.min(8, sessionId.length()))
                + "...) not found or not available for joining.",
            null);
      }
      if (joiningClient.getAssociatedGameSession() != null) {
        return new JoinGameResponseDTO(false, "You are already in a game or lobby.", null);
      }
      // Prevent joining own game if somehow listed (shouldn't be)
      if (sessionToJoin.getPlayer1() != null
          && sessionToJoin.getPlayer1().getPlayerId().equals(joiningClient.getPlayerId())) {
        return new JoinGameResponseDTO(false, "Cannot join your own game.", null);
      }

      if (sessionToJoin.addPlayer(
          joiningClient)) { // GameSession.addPlayer handles transitioning state
        publicLobbiesById.remove(sessionId); // It's no longer just a public lobby waiting for P2.
        server.log(
            "Player " + joiningClient.getDisplayId() + " joined public session " + sessionId);
        return new JoinGameResponseDTO(
            true,
            "Successfully joined game: "
                + sessionToJoin.getCaseTitle()
                + ". Waiting for game to start...",
            sessionId);
      } else {
        // addPlayer could fail if, in a race condition, it became full *just* before this.
        return new JoinGameResponseDTO(
            false,
            "Failed to join game session (it might have just become full or an error occurred).",
            null);
      }
    } finally {
      managerLock.unlock();
    }
  }

  /** Allows a client to join an existing private game using a code. */
  public JoinGameResponseDTO joinPrivateGame(ClientSession joiningClient, String gameCode) {
    managerLock.lock();
    try {
      String normalizedCode = gameCode.toUpperCase(); // Codes are case-insensitive.
      String sessionId = privateGameCodeToSessionId.get(normalizedCode);
      if (sessionId == null) {
        return new JoinGameResponseDTO(
            false, "Private game with code '" + normalizedCode + "' not found.", null);
      }
      GameSession sessionToJoin =
          activeSessionsById.get(sessionId); // Private games are in activeSessions from creation.
      if (sessionToJoin == null
          || sessionToJoin.getState() != GameSessionState.WAITING_FOR_PLAYERS
          || sessionToJoin.isFull()) {
        return new JoinGameResponseDTO(
            false, "Private game not found or not available for joining.", null);
      }
      if (joiningClient.getAssociatedGameSession() != null) {
        return new JoinGameResponseDTO(false, "You are already in a game or lobby.", null);
      }
      if (sessionToJoin.getPlayer1() != null
          && sessionToJoin.getPlayer1().getPlayerId().equals(joiningClient.getPlayerId())) {
        return new JoinGameResponseDTO(false, "Cannot join your own game.", null);
      }

      if (sessionToJoin.addPlayer(joiningClient)) {
        // No need to remove from publicLobbiesById as private games aren't there.
        // privateGameCodeToSessionId entry remains until session ends.
        server.log(
            "Player "
                + joiningClient.getDisplayId()
                + " joined private session "
                + sessionId
                + " with code "
                + normalizedCode);
        return new JoinGameResponseDTO(
            true,
            "Successfully joined private game: "
                + sessionToJoin.getCaseTitle()
                + ". Waiting for game to start...",
            sessionId);
      } else {
        return new JoinGameResponseDTO(
            false,
            "Failed to join private game session (it might have just become full or an error occurred).",
            null);
      }
    } finally {
      managerLock.unlock();
    }
  }

  // --- Session Lifecycle & Client Management ---

  /** Handles client disconnects by notifying the relevant GameSession. */
  public void handleClientDisconnect(ClientSession client) {
    if (client == null) return;
    GameSession session = client.getAssociatedGameSession();
    if (session != null) {
      server.log(
          "Notifying session "
              + session.getSessionId()
              + " about disconnect of "
              + client.getDisplayId());
      session.handlePlayerDisconnect(client); // GameSession will call back to manager's endSession.
    } else {
      server.log(
          "Client " + client.getDisplayId() + " disconnected but was not in any game session.");
    }
  }

  /**
   * Ends a game session and removes it from all tracking maps. Called by GameSession itself (e.g.,
   * on player exit/disconnect) or by admin commands.
   */
  public void endSession(String sessionId, String reason) {
    managerLock.lock(); // Protect all session maps.
    try {
      GameSession session = activeSessionsById.remove(sessionId);
      if (session != null) {
        publicLobbiesById.remove(sessionId); // Remove if it was a public lobby.
        if (session.getGameCode() != null) {
          privateGameCodeToSessionId.remove(session.getGameCode());
        }
        session.endSession(reason); // Tell session to do its internal cleanup and notify players.
        server.log(
            "Session " + sessionId + " fully ended and removed from manager. Reason: " + reason);

        // Persistence: GameSession's endSession might trigger a save if it ended abruptly.
        // Or saveAllActiveGames is called on server shutdown.
      } else {
        server.log(
            "Attempted to end session "
                + sessionId
                + ", but it was not found in activeSessionsById.");
      }
    } finally {
      managerLock.unlock();
    }
  }

  /** Called by GameSession to notify manager about host name change for public lobbies. */
  public void updatePublicGameHostName(String sessionId, String newHostDisplayName) {
    managerLock.lock();
    try {
      GameSession session = publicLobbiesById.get(sessionId);
      if (session != null && session.getPlayer1() != null) {
        // If I were caching PublicGameInfoDTOs, I'd update the cache entry here.
        // Since getPublicLobbiesInfo() generates dynamically using ClientSession.getDisplayId(),
        // this method mainly just needs to log that the name change will be reflected.
        // The actual ClientSession displayId was updated by processUpdateDisplayName.
        server.log(
            "Manager: Host name update for public session "
                + sessionId
                + " to '"
                + newHostDisplayName
                + "'. Listing will refresh on next query.");
      }
    } finally {
      managerLock.unlock();
    }
  }

  // --- Command Processing (for commands before joining a session) ---

  /** Processes commands from clients not yet in a specific game session (lobby commands). */
  public void processLobbyCommand(ClientSession sender, Command command) {
    // PlayerId should already be set on command by GameServer.
    server.log(
        "Processing Lobby Command: "
            + command.getClass().getSimpleName()
            + " from "
            + sender.getDisplayId());

    if (command instanceof RequestCaseListCommand) {
      // Client wants to know what cases it can host.
      // The boolean in RequestCaseListCommand isn't used by manager here,
      // client stores its own intent for public/private.
      sender.send(new AvailableCasesDTO(getAvailableCaseInfoDTOs()));
    } else if (command instanceof HostGameCommand) {
      HostGameRequestDTO req = ((HostGameCommand) command).getPayload();
      HostGameResponseDTO resp = createGame(sender, req.getCaseTitle(), req.isPublic());
      sender.send(resp);
      // If private game hosted successfully, also send a TextMessage with the code.
      if (resp.isSuccess() && !req.isPublic() && resp.getGameCode() != null) {
        sender.send(
            new TextMessage(
                "Private game code: " + resp.getGameCode() + ". Share it with your friend!",
                false));
      }
    } else if (command instanceof ListPublicGamesCommand) {
      sender.send(new PublicGamesListDTO(getPublicLobbiesInfo()));
    } else if (command instanceof JoinPublicGameCommand) {
      JoinPublicGameRequestDTO req = ((JoinPublicGameCommand) command).getPayload();
      sender.send(joinPublicGame(sender, req.getSessionId()));
    } else if (command instanceof JoinPrivateGameCommand) {
      JoinPrivateGameRequestDTO req = ((JoinPrivateGameCommand) command).getPayload();
      sender.send(joinPrivateGame(sender, req.getGameCode()));
    } else if (command instanceof UpdateDisplayNameCommand) {
      UpdateDisplayNameRequestDTO req = ((UpdateDisplayNameCommand) command).getPayload();
      String newName = req.getNewDisplayName();
      String oldName = sender.getDisplayId();
      if (newName != null
          && !newName.equals(oldName)
          && !newName.trim().isEmpty()
          && newName.length() < 25) {
        sender.setDisplayId(newName); // Update ClientSession directly.
        server.log(
            "Lobby: Player " + sender.getPlayerId() + " (was " + oldName + ") is now " + newName);
        sender.send(new PlayerNameChangedDTO(sender.getPlayerId(), oldName, newName)); // Confirm.
      } else {
        sender.send(new TextMessage("Invalid new display name.", true));
      }
    } else if (command instanceof ExitCommand) {
      server.log(
          "Player " + sender.getDisplayId() + " sent ExitCommand from lobby. Disconnecting.");
      sender.send(new TextMessage("Disconnecting from server.", false));
      try {
        sender.getChannel().close();
      } // GameServer.cleanupClient will handle full cleanup.
      catch (IOException e) {
        server.log("IOException closing channel for lobby exit: " + sender.getPlayerId());
      }
    }
    // AddCaseCommand from client not supported for lobby.
    else {
      sender.send(
          new TextMessage(
              "Command '"
                  + command.getClass().getSimpleName().replace("Command", "")
                  + "' not valid in lobby or unknown.",
              true));
      server.log(
          "Unhandled lobby command: "
              + command.getClass().getSimpleName()
              + " from "
              + sender.getPlayerId());
    }
  }

  // --- Persistence ---
  /**
   * Saves all currently active game sessions. Called by ServerMain on shutdown or by admin command.
   */
  public void saveAllActiveGames() {
    /* ... (as before) ... */
    server.log("Attempting to save all active game sessions...");
    managerLock.lock(); // Lock to safely iterate activeSessionsById
    try {
      List<GameSession> sessionsToSave =
          new ArrayList<>(
              activeSessionsById.values()); // Copy to avoid CME if a session ends during save
      sessionsToSave.stream()
          .filter(
              session ->
                  session.getState() == GameSessionState.ACTIVE
                      || session.getState()
                          == GameSessionState
                              .IN_LOBBY_AWAITING_START) // Only save truly active/ready games
          .forEach(
              session -> {
                try {
                  persistenceManager.saveGame(session.getGameContext().getGameStateForSaving());
                  server.log("Saved session: " + session.getSessionId());
                } catch (Exception e) { // Catch broad exceptions during save
                  server.logError("Error saving session " + session.getSessionId(), e);
                }
              });
    } finally {
      managerLock.unlock();
    }
  }
}
