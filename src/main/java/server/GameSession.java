package server;

import common.commands.*;
import common.dto.*;
import JsonDTO.CaseFile;
import extractors.BuildingExtractor;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import Core.GameObject; // For populating RoomDescriptionDTO
import Core.Room;       // For populating RoomDescriptionDTO

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GameSession {
    private final String sessionId;
    private final String caseTitle;
    private final CaseFile caseFile;
    private final GameContextServer gameContext;

    private ClientSession player1;
    private ClientSession player2;
    private final ReentrantLock sessionLock = new ReentrantLock();

    private GameSessionState state;
    private String gameCode;

    private final GameSessionManager sessionManager;
    private final GameServer server;

    public GameSession(String caseTitle, CaseFile caseFile, ClientSession hostPlayer, boolean isPublic, GameSessionManager manager, GameServer server) {
        this.sessionId = UUID.randomUUID().toString();
        this.caseTitle = Objects.requireNonNull(caseTitle, "Case title cannot be null");
        this.caseFile = Objects.requireNonNull(caseFile, "CaseFile DTO cannot be null");
        this.sessionManager = Objects.requireNonNull(manager, "GameSessionManager cannot be null");
        this.server = Objects.requireNonNull(server, "GameServer cannot be null");
        this.state = GameSessionState.LOADING;

        this.player1 = Objects.requireNonNull(hostPlayer, "Host player cannot be null");
        hostPlayer.setAssociatedGameSession(this);

        if (!isPublic) {
            this.gameCode = generateGameCode();
            log("Private game session created with code: " + this.gameCode);
        } else {
            log("Public game session created.");
        }

        this.gameContext = new GameContextServer(this, this.caseFile, hostPlayer.getPlayerId(), null);

        if (!loadCaseDataIntoContext()) {
            this.state = GameSessionState.ERROR;
            log("CRITICAL: Failed to load case data for new session " + sessionId + ". Session set to ERROR state.");
            hostPlayer.send(new TextMessage("Error: Failed to initialize the game case data. The session cannot start.", true));
        } else {
            this.state = GameSessionState.WAITING_FOR_PLAYERS;
            log("Session created for case '" + caseTitle + "'. Waiting for Player 2. Host: " + hostPlayer.getDisplayId());
            hostPlayer.send(new HostGameResponseDTO(true, "Game hosted successfully. Waiting for another player." + (isPublic ? "" : " Code: " + this.gameCode), this.gameCode, this.sessionId));
        }
    }

    public ClientSession getPlayer1() { // Public getter for player1 (host)
        sessionLock.lock();
        try {
            return player1;
        } finally {
            sessionLock.unlock();
        }
    }

    private boolean loadCaseDataIntoContext() {
        log("Loading case data into context for session: " + sessionId);
        gameContext.resetForNewCaseLoad();

        try {
            if (!BuildingExtractor.loadBuilding(this.caseFile, this.gameContext)) {
                log("Failed to load building for session " + sessionId);
                return false;
            }
            GameObjectExtractor.loadObjects(this.caseFile, this.gameContext);
            SuspectExtractor.loadSuspects(this.caseFile, this.gameContext);
        } catch (IllegalStateException e) {
            log("Error loading suspects for session " + sessionId + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            log("Unexpected error during case data loading for session " + sessionId + ": " + e.getMessage());
            // *** FIXED: Use GameServer's logError method ***
            server.logError("Stack trace for unexpected loading error in session " + sessionId, e);
            return false;
        }
        gameContext.initializePlayerStartingState();
        log("Case data loaded successfully for session: " + sessionId);
        return true;
    }

    public void notifyNameChangeToManagerIfHost(String playerId, String newDisplayName) {
        if (player1 != null && player1.getPlayerId().equals(playerId)) { // If the host's name changed
            if (sessionManager != null && this.getGameCode() == null) { // And it's a public game
                sessionManager.updatePublicGameHostName(this.sessionId, newDisplayName);
            }
        }
    }

    private String generateGameCode() {
        String chars = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }

    public String getSessionId() { return sessionId; }
    public String getCaseTitle() { return caseTitle; }
    public String getGameCode() { return gameCode; }
    public GameSessionState getState() { return state; }
    public GameContextServer getGameContext() { return gameContext; }
    public GameServer getServer() { return server; }

    public boolean addPlayer(ClientSession newPlayer) {
        if (newPlayer == null) { /* ... */ return false; }
        sessionLock.lock();
        try {
            // ... (existing checks: player2 != null, state != WAITING, host joining as P2) ...
            if (player2 != null || this.state != GameSessionState.WAITING_FOR_PLAYERS || (player1 != null && player1.getPlayerId().equals(newPlayer.getPlayerId()))) {
                // Simplified condition for brevity, original checks were more verbose and good
                String msg = player2 != null ? "Session is full." :
                        this.state != GameSessionState.WAITING_FOR_PLAYERS ? "Session not accepting players." :
                                "You cannot join your own session as P2.";
                newPlayer.send(new JoinGameResponseDTO(false, msg, null));
                log("Add player " + newPlayer.getDisplayId() + " failed: " + msg);
                return false;
            }


            player2 = newPlayer;
            newPlayer.setAssociatedGameSession(this);
            log("Player 2 (" + player2.getDisplayId() + ", ID: " + player2.getPlayerId() + ") joined session " + sessionId);

            this.gameContext.setPlayerIds(player1.getPlayerId(), player2.getPlayerId());
            this.gameContext.initializePlayerStartingState(); // Good to call here to ensure P2 is set

            newPlayer.send(new JoinGameResponseDTO(true, "Successfully joined game: " + caseTitle + " with " + player1.getDisplayId(), this.sessionId));

            // --- MODIFIED LobbyUpdateDTO creation ---
            List<String> displayIds = getPlayerDisplayIds();
            List<String> actualIds = getPlayerActualIds(); // New helper method
            String hostId = (player1 != null) ? player1.getPlayerId() : null;

            if (player1 != null) { // Notify player1 that player2 has joined
                player1.send(new LobbyUpdateDTO(
                        newPlayer.getDisplayId() + " has joined your game!",
                        displayIds,
                        actualIds,
                        hostId,
                        false // Game is not starting *yet*, just lobby update
                ));
            }
            // --- END MODIFICATION ---

            startGameSession(); // This will send another LobbyUpdateDTO indicating game is starting
            return true;
        } finally {
            sessionLock.unlock();
        }
    }

    private void startGameSession() {
        if (!isFull() || this.state != GameSessionState.WAITING_FOR_PLAYERS) {
            log("Pre-condition fail for startGameSession. State: " + this.state + ", Full: " + isFull() + ". Not starting.");
            return;
        }
        // *** CHANGE: Set to IN_LOBBY_AWAITING_START ***
        // The game becomes truly ACTIVE only after the host sends 'start case' and
        // GameContextServer.setCaseStarted(true) calls gameSession.setSessionState(GameSessionState.ACTIVE).
        this.setSessionState(GameSessionState.IN_LOBBY_AWAITING_START);
        log("Session " + sessionId + " is now " + this.state + " with players: " + player1.getDisplayId() + ", " + player2.getDisplayId() + ". Ready for 'start case'.");

        // ... (rest of broadcasting LobbyUpdateDTO and Case Invitation) ...
        List<String> displayIds = getPlayerDisplayIds();
        List<String> actualIds = getPlayerActualIds();
        String hostId = (player1 != null) ? player1.getPlayerId() : null;

        LobbyUpdateDTO gameReadyUpdate = new LobbyUpdateDTO(
                "Both players are here! " + String.join(" and ", displayIds) + " are in the lobby.",
                displayIds,
                actualIds,
                hostId,
                true // true because session is formed and ready for 'start case'
        );
        broadcast(gameReadyUpdate, null);

        broadcast(new TextMessage(
                        "--- Case Invitation ---\n" + caseFile.getInvitation() +
                                "\n\nOne player (typically the host, " + player1.getDisplayId() + ") should type 'start case' to begin the investigation.", false),
                null
        );
    }


    private List<String> getPlayerActualIds() {
        List<String> ids = new ArrayList<>();
        if (player1 != null) ids.add(player1.getPlayerId());
        if (player2 != null) ids.add(player2.getPlayerId());
        return ids;
    }

    public boolean isFull() {
        return player1 != null && player2 != null;
    }

    public List<String> getPlayerDisplayIds() {
        List<String> ids = new ArrayList<>();
        if (player1 != null) ids.add(player1.getDisplayId());
        if (player2 != null) ids.add(player2.getDisplayId());
        return ids;
    }

    public void handlePlayerDisconnect(ClientSession disconnectedClient) {
        if (disconnectedClient == null) return;
        sessionLock.lock();
        try {
            String leavingPlayerDisplayId = disconnectedClient.getDisplayId();
            String leavingPlayerId = disconnectedClient.getPlayerId();
            boolean wasPlayer1 = player1 != null && player1.getPlayerId().equals(leavingPlayerId);
            boolean wasPlayer2 = player2 != null && player2.getPlayerId().equals(leavingPlayerId);

            if (!wasPlayer1 && !wasPlayer2) {
                return;
            }

            log("Player " + leavingPlayerDisplayId + " (ID: " + leavingPlayerId + ") disconnected from session " + sessionId);
            disconnectedClient.setAssociatedGameSession(null);

            if (wasPlayer1) player1 = null;
            if (wasPlayer2) player2 = null;
            gameContext.setPlayerIds(player1 != null ? player1.getPlayerId() : null, player2 != null ? player2.getPlayerId() : null);

            if (this.state == GameSessionState.ACTIVE || this.state == GameSessionState.WAITING_FOR_PLAYERS) {
                this.state = GameSessionState.ENDED_ABANDONED;
                ClientSession remainingPlayer = (player1 != null) ? player1 : player2;

                if (remainingPlayer != null) {
                    remainingPlayer.send(new TextMessage(leavingPlayerDisplayId + " has left the game. The session has ended.", false));
                    remainingPlayer.send(new ReturnToLobbyDTO("Your opponent disconnected. Session ended."));
                    remainingPlayer.setAssociatedGameSession(null);
                }
                log("Session " + sessionId + " ended due to player disconnect.");
                sessionManager.endSession(this.sessionId, "Player " + leavingPlayerDisplayId + " disconnected.");
            } else {
                log("Player " + leavingPlayerDisplayId + " disconnected during non-active state: " + this.state + ". Informing manager.");
                sessionManager.endSession(this.sessionId, "Player disconnected during session state: " + this.state);
            }
        } finally {
            sessionLock.unlock();
        }
    }

    public void playerRequestsExit(String playerId) {
        sessionLock.lock();
        try {
            ClientSession exitingPlayer = getClientSessionById(playerId);
            if (exitingPlayer == null) {
                log("Received exit request for unknown player ID " + playerId + " in session " + sessionId);
                return;
            }
            log("Player " + exitingPlayer.getDisplayId() + " (ID: " + playerId + ") requests to exit session " + sessionId);

            this.state = GameSessionState.ENDED_ABANDONED;
            exitingPlayer.send(new TextMessage("You have left the game session.", false));
            exitingPlayer.send(new ReturnToLobbyDTO("You have exited the game."));
            exitingPlayer.setAssociatedGameSession(null);

            ClientSession otherPlayer = getOtherPlayer(playerId);
            if (otherPlayer != null) {
                otherPlayer.send(new TextMessage(exitingPlayer.getDisplayId() + " has exited the game. The session has ended.", false));
                otherPlayer.send(new ReturnToLobbyDTO(exitingPlayer.getDisplayId() + " exited. Session ended."));
                otherPlayer.setAssociatedGameSession(null);
            }

            if (player1 == exitingPlayer) player1 = null;
            if (player2 == exitingPlayer) player2 = null;
            gameContext.setPlayerIds(player1 != null ? player1.getPlayerId() : null, player2 != null ? player2.getPlayerId() : null);

            sessionManager.endSession(this.sessionId, "Player " + exitingPlayer.getDisplayId() + " exited.");
        } finally {
            sessionLock.unlock();
        }
    }

    public void setSessionState(GameSessionState newState) {
        sessionLock.lock();
        try {
            if (this.state != newState) {
                log("Session state changing from " + this.state + " to " + newState);
                this.state = newState;
                // Optionally, broadcast a generic "Game state updated to ACTIVE" if clients need it,
                // but usually the flow of DTOs (like initial room desc) implies this.
            }
        } finally {
            sessionLock.unlock();
        }
    }

    // Inside server.GameSession.java
// Inside server.GameSession.java

    public void processCommand(Command command, String playerId) {
        if (command == null || playerId == null) {
            log("Error: Null command or playerId in processCommand for session " + sessionId);
            return;
        }
        sessionLock.lock();
        try {
            command.setPlayerId(playerId); // Ensure player ID from network layer is set on the command

            boolean commandAllowed = false;
            String commandSimpleName = command.getClass().getSimpleName().replace("Command", "");

            // Log the attempt before checking allowance for better traceability
            log("Attempting to process command " + commandSimpleName + " for player " + playerId +
                    " in session " + sessionId + " (Current Session State: " + this.state + ")");

            if (this.state == GameSessionState.ACTIVE) {
                // If session is ACTIVE, most game commands are allowed.
                // Specific commands like InitiateFinalExamCommand will have further checks
                // inside GameContextServer (e.g., isPlayerHost, isCaseStarted).
                commandAllowed = true;
            } else if (this.state == GameSessionState.IN_LOBBY_AWAITING_START ||
                    (this.state == GameSessionState.WAITING_FOR_PLAYERS && isFull())) {
                // In these pre-active (lobby) states, only allow specific transitional or lobby management commands.
                // The game becomes ACTIVE when GameContextServer.setCaseStarted(true) calls
                // gameSession.setSessionState(GameSessionState.ACTIVE).
                if (command instanceof StartCaseCommand ||          // Host starting the case
                        command instanceof RequestStartCaseCommand ||   // Guest requesting start
                        command instanceof ExitCommand ||               // Anyone can exit lobby
                        command instanceof HelpCommand) {               // Help is generally fine
                    commandAllowed = true;
                }
                // InitiateFinalExamCommand and RequestInitiateExamCommand should typically only be allowed
                // AFTER the case is started and GameSession.state is ACTIVE.
                // If you want to allow "request final exam" from the lobby state, add it here.
                // else if (command instanceof RequestInitiateExamCommand) { commandAllowed = true; }
            }
            // GameSessionState.ERROR, ENDED_*, LOADING might implicitly deny commands
            // unless explicitly allowed for some cleanup or info commands.

            if (!commandAllowed) {
                ClientSession sender = getClientSessionById(playerId);
                String denialMessage = "Command '" + commandSimpleName +
                        "' is not allowed in the current session state: " + this.state;
                if (sender != null) {
                    sender.send(new TextMessage(denialMessage, true));
                }
                log(denialMessage + " (Player: " + playerId + ")");
                return;
            }

            // If command is allowed, proceed to execute it via the game context
            log("Command " + commandSimpleName + " allowed. Executing via GameContextServer...");
            gameContext.executeCommand(command); // GameContextServer will do further validation (e.g. host checks)

        } finally {
            sessionLock.unlock();
        }
    }

    public void processChatMessage(ChatMessage chatMessage) {
        if (chatMessage == null) return;
        sessionLock.lock();
        try {
            if (this.state != GameSessionState.ACTIVE && this.state != GameSessionState.WAITING_FOR_PLAYERS) {
                ClientSession sender = getClientSessionByDisplayId(chatMessage.getSenderDisplayId());
                if(sender != null) sender.send(new TextMessage("Cannot send chat. Session not in lobby or active game.", true));
                return;
            }
            log("Chat in session " + sessionId + ": <" + chatMessage.getSenderDisplayId() + "> " + chatMessage.getText());
            broadcast(chatMessage, null);
        } finally {
            sessionLock.unlock();
        }
    }

    public void broadcast(Serializable dto, String excludePlayerId) {
        sessionLock.lock();
        try {
            if (player1 != null && (excludePlayerId == null || !player1.getPlayerId().equals(excludePlayerId))) {
                player1.send(dto);
            }
            if (player2 != null && (excludePlayerId == null || !player2.getPlayerId().equals(excludePlayerId))) {
                player2.send(dto);
            }
        } finally {
            sessionLock.unlock();
        }
    }

    public ClientSession getClientSessionById(String playerId) {
        if (playerId == null) return null;
        if (player1 != null && player1.getPlayerId().equals(playerId)) return player1;
        if (player2 != null && player2.getPlayerId().equals(playerId)) return player2;
        return null;
    }

    private ClientSession getClientSessionByDisplayId(String displayId) {
        if (displayId == null) return null;
        if (player1 != null && player1.getDisplayId().equals(displayId)) return player1;
        if (player2 != null && player2.getDisplayId().equals(displayId)) return player2;
        return null;
    }

    public ClientSession getOtherPlayer(String currentPlayerId) {
        if (currentPlayerId == null) return null;
        if (player1 != null && player1.getPlayerId().equals(currentPlayerId)) return player2;
        if (player2 != null && player2.getPlayerId().equals(currentPlayerId)) return player1;
        return null;
    }

    public void requestSaveGame() {
        sessionLock.lock();
        try {
            if (this.state != GameSessionState.ACTIVE) {
                log("Save request for session " + sessionId + " ignored, session not active. State: " + this.state);
                return;
            }
            log("Requesting save for session " + sessionId + "...");
            GameStateData stateToSave = gameContext.getGameStateForSaving();
            sessionManager.saveGameSessionState(this.sessionId, stateToSave);
            broadcast(new TextMessage("Game progress has been saved by the server.", false), null);
        } finally {
            sessionLock.unlock();
        }
    }

    public void applyLoadedState(GameStateData loadedData) {
        if (loadedData == null) {
            log("Error: Attempted to apply null loaded state to session " + sessionId);
            return;
        }
        sessionLock.lock();
        try {
            log("Applying loaded game state to session " + sessionId + " for case: " + loadedData.getCaseTitle());

            List<String> loadedPlayerIds = loadedData.getPlayerIds();
            String p1LoadedId = loadedPlayerIds.size() > 0 ? loadedPlayerIds.get(0) : null;
            String p2LoadedId = loadedPlayerIds.size() > 1 ? loadedPlayerIds.get(1) : null;

            // Important: Ensure current player1 and player2 ClientSessions correspond to these loaded IDs.
            // This mapping happens in GameSessionManager when players rejoin a loaded game.
            // Here we assume player1 and player2 objects of this GameSession are already the correct rejoining clients.
            // And their IDs match p1LoadedId and p2LoadedId.
            gameContext.setPlayerIds(this.player1 != null ? this.player1.getPlayerId() : null,
                    this.player2 != null ? this.player2.getPlayerId() : null);


            gameContext.applyGameState(loadedData);
            this.state = GameSessionState.ACTIVE;
            log("Game state successfully applied to session " + sessionId + ". Session is ACTIVE.");

            broadcast(new TextMessage("Previously saved game '" + caseTitle + "' has been loaded. Welcome back!", false), null);

            // Send current room description to rejoining players
            if (player1 != null && player1.getPlayerId().equals(p1LoadedId)) { // Check if current P1 is the loaded P1
                sendCurrentRoomDescriptionToPlayer(player1.getPlayerId());
            }
            if (player2 != null && player2.getPlayerId().equals(p2LoadedId)) { // Check if current P2 is the loaded P2
                sendCurrentRoomDescriptionToPlayer(player2.getPlayerId());
            }

        } finally {
            sessionLock.unlock();
        }
    }

    // Helper to send room description, reducing code duplication
    private void sendCurrentRoomDescriptionToPlayer(String playerId) {
        Room playerCurrentRoom = gameContext.getCurrentRoomForPlayer(playerId);
        if (playerCurrentRoom != null) {
            List<String> objectNames = playerCurrentRoom.getObjects().values().stream()
                    .map(GameObject::getName)
                    .collect(Collectors.toList());
            String occupantsStr = gameContext.getOccupantsDescriptionInRoom(playerCurrentRoom, playerId);
            List<String> occupantNamesList = new ArrayList<>();
            if (occupantsStr != null && !occupantsStr.equalsIgnoreCase("Occupants: None") && occupantsStr.startsWith("Occupants: ")) {
                String[] names = occupantsStr.substring("Occupants: ".length()).split(",\\s*");
                for (String name : names) {
                    if (!name.trim().isEmpty()) occupantNamesList.add(name.trim());
                }
            }
            Map<String, String> exits = playerCurrentRoom.getNeighbors().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
            gameContext.sendResponseToPlayer(playerId, new RoomDescriptionDTO(
                    playerCurrentRoom.getName(),
                    playerCurrentRoom.getDescription(),
                    objectNames,
                    occupantNamesList,
                    exits
            ));
        }
    }


    public void endSession(String reason) {
        sessionLock.lock();
        try {
            if (this.state == GameSessionState.ENDED_ABANDONED ||
                    this.state == GameSessionState.ENDED_NORMAL ||
                    this.state == GameSessionState.ERROR) {
                return;
            }

            GameSessionState previousState = this.state;
            this.state = GameSessionState.ENDED_NORMAL;
            if (reason.toLowerCase().contains("abandoned") || reason.toLowerCase().contains("disconnect") || reason.toLowerCase().contains("exited")) {
                this.state = GameSessionState.ENDED_ABANDONED;
            }
            log("Session " + sessionId + " transitioning from " + previousState + " to " + this.state + ". Reason: " + reason);

            // --- MODIFIED LobbyUpdateDTO creation ---
            List<String> finalDisplayIds = getPlayerDisplayIds(); // Will be empty if players nulled out before this call
            List<String> finalActualIds = getPlayerActualIds();
            String finalHostId = (player1 != null) ? player1.getPlayerId() : null; // Host ID at the point of ending

            LobbyUpdateDTO endMessage = new LobbyUpdateDTO(
                    "Game session '" + caseTitle + "' has ended. " + reason,
                    finalDisplayIds,
                    finalActualIds,
                    finalHostId,
                    false // Game is not starting, it's ended
            );
            // --- END MODIFICATION ---
            broadcast(endMessage, null); // Notify any still connected (though unlikely if already handled)

            if (player1 != null) { player1.setAssociatedGameSession(null); player1 = null; }
            if (player2 != null) { player2.setAssociatedGameSession(null); player2 = null; }
            gameContext.setPlayerIds(null, null);
        } finally {
            sessionLock.unlock();
        }
    }


    private void log(String message) {
        // Ensure server object is not null before calling log on it, defensively.
        if (this.server != null) {
            this.server.log("[SESS:" + sessionId + "] " + message);
        } else {
            // Fallback if server somehow isn't set, though constructor requires it.
            System.out.println("[SESS:" + sessionId + " NO_SERVER_LOG] " + message);
        }
    }
}