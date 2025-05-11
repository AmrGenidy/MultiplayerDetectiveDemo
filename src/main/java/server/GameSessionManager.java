package server;

import JsonDTO.CaseFile; // For CaseFile
import common.dto.*; // All DTOs
import extractors.CaseLoader; // For loading cases
import common.commands.Command;
import java.io.IOException;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GameSessionManager {
    private final Map<String, CaseFile> availableCases; // Title -> CaseFile
    private final Map<String, GameSession> activeSessionsById; // sessionId -> GameSession
    private final Map<String, GameSession> publicLobbiesById; // sessionId -> GameSession (in WAITING_FOR_PLAYERS state)
    private final Map<String, String> privateGameCodeToSessionId; // gameCode -> sessionId

    private final ReentrantLock managerLock = new ReentrantLock(); // Renamed from sessionLock to avoid confusion
    private final GameServer server;

    private final PersistenceManager persistenceManager;

    private static final String CASES_DIRECTORY = "cases"; // Server's cases directory

    public GameSessionManager(GameServer server, PersistenceManager persistenceManager) {
        this.server = server;
        this.persistenceManager = persistenceManager;
        this.availableCases = new ConcurrentHashMap<>();
        this.activeSessionsById = new ConcurrentHashMap<>();
        this.publicLobbiesById = new ConcurrentHashMap<>();
        this.privateGameCodeToSessionId = new ConcurrentHashMap<>();
        loadAllAvailableCases();
    }

    private void loadAllAvailableCases() {
        List<CaseFile> cases = CaseLoader.loadCases(CASES_DIRECTORY);
        for (CaseFile cf : cases) {
            availableCases.put(cf.getTitle().toLowerCase(), cf);
        }
        server.log("Loaded " + availableCases.size() + " cases from '" + CASES_DIRECTORY + "'.");
    }

    public void reloadCases() { // For admin command to refresh cases without server restart
        availableCases.clear();
        loadAllAvailableCases();
    }



    // Inside server.GameSessionManager.java
    public void updatePublicGameHostName(String sessionId, String newHostDisplayName) {
        managerLock.lock(); // Use the manager's own lock
        try {
            GameSession session = publicLobbiesById.get(sessionId);
            // Check if the session is still in the publicLobbiesById map
            // and if player1 (the host) is still present in that session.
            if (session != null && session.getPlayer1() != null) { // Use the new getter
                // The host's display name is actually on session.getPlayer1().getDisplayId()
                // This method is called AFTER ClientSession's displayId has been updated.
                // The primary purpose here might be to refresh a cached list of PublicGameInfoDTOs
                // if GameSessionManager caches them.
                // If PublicGameInfoDTOs are generated on-the-fly when requested (e.g., by ListPublicGamesCommand),
                // then this method might not need to do much other than log, as the next list generation
                // will pick up the new name from ClientSession.

                server.log("Manager: Host name update received for public session " + sessionId +
                        ". New name: " + newHostDisplayName +
                        ". Public listing will reflect this on next query.");

                // If you ARE caching PublicGameInfoDTOs in GameSessionManager, update it here:
                // Example:
                // PublicGameInfoDTO cachedInfo = findCachedPublicGameInfo(sessionId);
                // if (cachedInfo != null) {
                //     // If PublicGameInfoDTO is immutable, create a new one and replace.
                //     // PublicGameInfoDTO updatedInfo = new PublicGameInfoDTO(newHostDisplayName, session.getCaseTitle(), sessionId);
                //     // replaceInCachedList(sessionId, updatedInfo);
                // }
            } else {
                server.log("Manager: updatePublicGameHostName called for session " + sessionId +
                        " which is not a public lobby or has no host.");
            }
        } finally {
            managerLock.unlock();
        }
    }

    public List<CaseInfoDTO> getAvailableCaseInfoDTOs() {
        return availableCases.values().stream()
                .map(cf -> new CaseInfoDTO(cf.getTitle(), cf.getDescription(), cf.getInvitation()))
                .collect(Collectors.toList());
    }

    public synchronized HostGameResponseDTO createGame(ClientSession hostClient, String caseTitle, boolean isPublic) {
        CaseFile selectedCaseFile = availableCases.get(caseTitle.toLowerCase());
        if (selectedCaseFile == null) {
            return new HostGameResponseDTO(false, "Case '" + caseTitle + "' not found on server.", null, null);
        }

        // Check if host is already in a session
        if (hostClient.getAssociatedGameSession() != null) {
            return new HostGameResponseDTO(false, "You are already in a game or lobby.", null, hostClient.getAssociatedGameSession().getSessionId());
        }

        GameSession newSession = new GameSession(selectedCaseFile.getTitle(), selectedCaseFile, hostClient, isPublic, this, server);
        if (newSession.getState() == GameSessionState.ERROR) { // If case loading failed in GameSession constructor
            return new HostGameResponseDTO(false, "Failed to initialize game session for case: " + caseTitle, null, null);
        }

        activeSessionsById.put(newSession.getSessionId(), newSession);
        if (isPublic) {
            publicLobbiesById.put(newSession.getSessionId(), newSession);
        } else {
            privateGameCodeToSessionId.put(newSession.getGameCode(), newSession.getSessionId());
        }
        server.log("New game session created: " + newSession.getSessionId() + (isPublic ? " (Public)" : " (Private Code: " + newSession.getGameCode() + ")") + " for case: " + caseTitle + " by host: " + hostClient.getPlayerId());
        return new HostGameResponseDTO(true, "Game hosted successfully. Waiting for opponent...", newSession.getGameCode(), newSession.getSessionId());
    }

    public synchronized JoinGameResponseDTO joinPublicGame(ClientSession joiningClient, String sessionId) {
        GameSession sessionToJoin = publicLobbiesById.get(sessionId);
        if (sessionToJoin == null || sessionToJoin.getState() != GameSessionState.WAITING_FOR_PLAYERS) {
            return new JoinGameResponseDTO(false, "Public game not found or not available for joining.", null);
        }
        if (joiningClient.getAssociatedGameSession() != null) {
            return new JoinGameResponseDTO(false, "You are already in a game or lobby.", null);
        }

        if (sessionToJoin.addPlayer(joiningClient)) {
            publicLobbiesById.remove(sessionId); // No longer a lobby, now an active game (or full)
            server.log("Player " + joiningClient.getPlayerId() + " joined public session " + sessionId);
            return new JoinGameResponseDTO(true, "Successfully joined game. Waiting for game to start...", sessionId);
        } else {
            return new JoinGameResponseDTO(false, "Failed to join game session (perhaps it just became full).", null);
        }
    }

    public synchronized JoinGameResponseDTO joinPrivateGame(ClientSession joiningClient, String gameCode) {
        String sessionId = privateGameCodeToSessionId.get(gameCode.toUpperCase()); // Assume codes are case-insensitive
        if (sessionId == null) {
            return new JoinGameResponseDTO(false, "Private game with code '" + gameCode + "' not found.", null);
        }
        GameSession sessionToJoin = activeSessionsById.get(sessionId);
        if (sessionToJoin == null || sessionToJoin.getState() != GameSessionState.WAITING_FOR_PLAYERS) {
            return new JoinGameResponseDTO(false, "Private game not found or not available for joining.", null);
        }
        if (joiningClient.getAssociatedGameSession() != null) {
            return new JoinGameResponseDTO(false, "You are already in a game or lobby.", null);
        }

        if (sessionToJoin.addPlayer(joiningClient)) {
            // Private game might not be in publicLobbiesById, but if addPlayer makes it full, it transitions state.
            server.log("Player " + joiningClient.getPlayerId() + " joined private session " + sessionId + " with code " + gameCode);
            return new JoinGameResponseDTO(true, "Successfully joined private game. Waiting for game to start...", sessionId);
        } else {
            return new JoinGameResponseDTO(false, "Failed to join private game session (perhaps it just became full).", null);
        }
    }

    public List<PublicGameInfoDTO> getPublicLobbiesInfo() {
        managerLock.lock(); // Good, lock is used
        try {
            return publicLobbiesById.values().stream()
                    .filter(session -> session.getState() == GameSessionState.WAITING_FOR_PLAYERS &&
                            session.getPlayer1() != null && // Ensure host (player1) exists
                            session.getPlayerDisplayIds().size() == 1) // Ensure only host is in (P2 hasn't joined)
                    .map(session -> {
                        ClientSession hostSession = session.getPlayer1(); // Get the host's ClientSession
                        String hostDisplayName = (hostSession != null) ? hostSession.getDisplayId() : "Unknown Host"; // Use its current displayId
                        return new PublicGameInfoDTO(
                                hostDisplayName,
                                session.getCaseTitle(),
                                session.getSessionId());
                    })
                    .collect(Collectors.toList());
        } finally {
            managerLock.unlock();
        }
    }

    public void handleClientDisconnect(ClientSession client) {
        if (client == null) {
            server.log("Warning: handleClientDisconnect called with null client.");
            return;
        }
        server.log("Handling disconnect for client: " + client.getDisplayId() + " (ID: " + client.getPlayerId() + ")");
        GameSession session = client.getAssociatedGameSession(); // Get the session the client was part of

        if (session != null) {
            // Delegate the disconnect logic to the GameSession itself
            // GameSession.handlePlayerDisconnect will:
            // 1. Remove the client from its player1/player2 slot.
            // 2. Update its internal state (e.g., to ENDED_ABANDONED).
            // 3. Notify the other player (if any).
            // 4. Call sessionManager.endSession() to have this manager clean it up.
            session.handlePlayerDisconnect(client);
        } else {
            server.log("Client " + client.getDisplayId() + " was not associated with any active game session upon disconnect.");
            // If the client was in a lobby queue or similar pre-game state,
            // that logic would be handled here too (e.g., removing from a waiting list).
        }
        // The client's actual network channel cleanup (closing socket, removing from selector)
        // is typically handled by GameServer when it detects the disconnect (e.g., IOException on read/write,
        // or if handlePlayerDisconnect in GameSession signals GameServer to do so).
        // GameServer's cleanupClient method would then call this GameSessionManager.handleClientDisconnect.
    }

    public synchronized void endSession(String sessionId, String reason) {
        GameSession session = activeSessionsById.remove(sessionId);
        if (session != null) {
            publicLobbiesById.remove(sessionId); // Remove from public lobbies if it was there
            // Remove from private code mapping
            if (session.getGameCode() != null) {
                privateGameCodeToSessionId.remove(session.getGameCode());
            }
            session.endSession(reason); // Ensure session itself knows it's ended (sends final msgs)
            server.log("Session " + sessionId + " fully ended and removed from manager. Reason: " + reason);

            // Optionally, attempt to save the game state if it was active and ended abruptly
            if (session.getState() == GameSessionState.ACTIVE || session.getState() == GameSessionState.ENDED_ABANDONED) {
                // persistenceManager.saveGame(session.getGameContext().getGameStateForSaving());
                // This call needs the GameStateData from gameContext.
                // session.requestSaveGame(); // GameSession calls persistenceManager
            }
        }
    }

    // For Persistence
    public void saveAllActiveGames() {
        server.log("Attempting to save all active game sessions...");
        activeSessionsById.values().stream()
                .filter(session -> session.getState() == GameSessionState.ACTIVE || session.getState() == GameSessionState.LOADING) // or any state worth saving
                .forEach(session -> {
                    try {
                        persistenceManager.saveGame(session.getGameContext().getGameStateForSaving());
                        server.log("Saved session: " + session.getSessionId());
                    } catch (Exception e) {
                        server.log("Error saving session " + session.getSessionId() + ": " + e.getMessage());
                    }
                });
    }

    public void loadGameSessionAndResume(String sessionId, ClientSession player1, ClientSession player2ToReconnect) {
        GameStateData loadedData = persistenceManager.loadGame(sessionId);
        if (loadedData == null) {
            server.log("Failed to load game state for session: " + sessionId);
            if (player1 != null) player1.send(new TextMessage("Failed to load saved game.", true));
            if (player2ToReconnect != null) player2ToReconnect.send(new TextMessage("Failed to load saved game.", true));
            return;
        }

        CaseFile caseFile = availableCases.get(loadedData.getCaseTitle().toLowerCase());
        if (caseFile == null) {
            server.log("Case file '" + loadedData.getCaseTitle() + "' for loaded session " + sessionId + " not found.");
            if (player1 != null) player1.send(new TextMessage("Case file for saved game not found on server.", true));
            if (player2ToReconnect != null) player2ToReconnect.send(new TextMessage("Case file for saved game not found on server.", true));
            return;
        }

        // Create a new GameSession instance or re-purpose an old one (new is cleaner)
        // This part is complex: need to map playerIds from loadedData to current ClientSessions
        // For simplicity, assume player1 is the one initiating resume or already connected.
        // player2ToReconnect might be null if P2 hasn't reconnected yet.

        // Placeholder for re-creating/populating a session
        server.log("Loaded game " + sessionId + ". Re-associating players and applying state is complex and needs careful implementation.");
        // GameSession resumedSession = new GameSession(loadedData.getCaseTitle(), caseFile, player1, false, this, server);
        // if (player2ToReconnect != null) resumedSession.addPlayer(player2ToReconnect);
        // resumedSession.applyLoadedState(loadedData);
        // activeSessionsById.put(sessionId, resumedSession);
        // ... notify players ...
        if (player1 != null) player1.send(new TextMessage("Game " + sessionId + " loaded (resume functionality placeholder).", false));
        if (player2ToReconnect != null) player2ToReconnect.send(new TextMessage("Game " + sessionId + " loaded (resume functionality placeholder).", false));

    }

    // Called by GameSession
    protected void saveGameSessionState(String sessionId, GameStateData gameStateData) {
        if (persistenceManager != null) {
            try {
                persistenceManager.saveGame(gameStateData);
                server.log("Successfully saved game state for session: " + sessionId);
            } catch (Exception e) {
                server.log("Error saving game state for session " + sessionId + ": " + e.getMessage());
            }
        }
    }

    public void processLobbyCommand(ClientSession sender, Command command) {
        // Ensure player ID is set on the command, though lobby commands might not always need it
        // if the sender ClientSession is already known.
        command.setPlayerId(sender.getPlayerId());

        server.log("Processing Lobby Command: " + command.getClass().getSimpleName() + " from " + sender.getDisplayId() + " (" + sender.getPlayerId() + ")");

        if (command instanceof common.commands.RequestCaseListCommand) {
            List<CaseInfoDTO> caseInfos = getAvailableCaseInfoDTOs();
            sender.send(new AvailableCasesDTO(caseInfos));
        } else if (command instanceof common.commands.HostGameCommand) {
            HostGameRequestDTO request = ((common.commands.HostGameCommand) command).getPayload();
            HostGameResponseDTO response = createGame(sender, request.getCaseTitle(), request.isPublic());
            sender.send(response);
            if (response.isSuccess() && !request.isPublic() && response.getGameCode() != null) {
                sender.send(new TextMessage("Your private game code is: " + response.getGameCode() + ". Share it with your friend!", false));
            }
        } else if (command instanceof common.commands.ListPublicGamesCommand) {
            List<PublicGameInfoDTO> publicLobbies = getPublicLobbiesInfo();
            sender.send(new PublicGamesListDTO(publicLobbies));
        } else if (command instanceof common.commands.JoinPublicGameCommand) {
            JoinPublicGameRequestDTO request = ((common.commands.JoinPublicGameCommand) command).getPayload();
            JoinGameResponseDTO response = joinPublicGame(sender, request.getSessionId());
            sender.send(response);
        } else if (command instanceof common.commands.JoinPrivateGameCommand) {
            JoinPrivateGameRequestDTO request = ((common.commands.JoinPrivateGameCommand) command).getPayload();
            JoinGameResponseDTO response = joinPrivateGame(sender, request.getGameCode());
            sender.send(response);
        }
        // Add handling for other pre-game/lobby commands here (e.g., AddCaseCommand if allowed from client)
        else if (command instanceof common.commands.AddCaseCommand) {
            // For security, client-sent AddCaseCommand should be heavily restricted or disallowed.
            // If allowed, it would need special handling, not direct file system access from client path.
            // e.g., server admin might use a different mechanism.
            sender.send(new TextMessage("'Add Case' from client is not supported in this manner.", true));
        }
        else if (command instanceof common.commands.UpdateDisplayNameCommand) { // <<< NEW HANDLER
            UpdateDisplayNameRequestDTO request = ((common.commands.UpdateDisplayNameCommand) command).getPayload();
            String newName = request.getNewDisplayName();
            String oldName = sender.getDisplayId();

            if (newName != null && !newName.equals(oldName) && !newName.trim().isEmpty() && newName.length() < 25) {
                sender.setDisplayId(newName);
                server.log("Lobby: Player " + sender.getPlayerId() + " (formerly " + oldName + ") changed display name to " + newName);
                // Broadcast to this client for confirmation, or if there's a global lobby, broadcast there.
                // For now, just confirm to sender. Server needs to know for future session creation.
                sender.send(new PlayerNameChangedDTO(sender.getPlayerId(), oldName, newName)); // Confirm change
            } else if (newName.equals(oldName)) {
                sender.send(new TextMessage("Your display name is already " + newName + ".", false));
            } else {
                sender.send(new TextMessage("New display name is invalid.", true));
            }
        }
        // Handle ExitCommand if player is in lobby (not yet in a game session)
        else if (command instanceof common.commands.ExitCommand) {
            server.log("Player " + sender.getDisplayId() + " (" + sender.getPlayerId() + ") sent ExitCommand from lobby. Disconnecting.");
            // The cleanupClient in GameServer (called on socket error/closure) will handle GameSessionManager.handleClientDisconnect
            // So here, we might just signal the client its request is acknowledged before the server closes connection.
            sender.send(new TextMessage("Disconnecting from server as per your request.", false));
            // Force close from server side after acknowledgement.
            // The GameServer's main loop error handling will catch IOException from this and call cleanupClient.
            try {
                sender.getChannel().close();
            } catch (IOException e) {
                server.log("IOException while trying to close channel for " + sender.getPlayerId() + " after lobby exit command.");
            }
        }
        else {
            sender.send(new TextMessage("This command '" + command.getClass().getSimpleName() + "' cannot be used now or is unknown.", true));
            server.log("Unhandled lobby command: " + command.getClass().getSimpleName() + " from " + sender.getPlayerId());
        }
    }

}