package client;

import client.util.CommandFactoryClient;
import client.util.CommandParserClient;
import common.NetworkConstants;
import common.SerializationUtils;
import common.commands.*;
import common.dto.*;

import java.io.IOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

public class GameClient implements Runnable {
    private final String host;
    private final int port;
    private SocketChannel channel;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<ClientState> currentState = new AtomicReference<>(ClientState.DISCONNECTED);

    private Thread networkListenerThread;
    private Scanner consoleScanner;

    private String hostPlayerIdInSession;

    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5; // Or your preferred number
    private static final long RECONNECT_DELAY_MS = 5000;

    private String playerId;
    private String playerDisplayId;
    private String currentSessionId;

    private List<CaseInfoDTO> availableCasesCache;
    private List<PublicGameInfoDTO> publicGamesCache;
    private int currentExamQuestionNumberBeingAnswered = -1;

    private ClientState preWaitingState; // To remember where to go back to on 'cancel'

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ReentrantLock consoleLock = new ReentrantLock();

    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.playerDisplayId = "Player" + (int) (Math.random() * 9000 + 1000);
    }

    @Override
    public void run() {
        this.consoleScanner = new Scanner(System.in); // Initialize scanner for the client's main thread
        printToConsole("Welcome, " + playerDisplayId + "!");
        // promptForDisplayNameChange(); // Optional

        // Initial connection attempt when client starts
        if (currentState.get() == ClientState.DISCONNECTED) {
            attemptConnect(); // This will set state to CONNECTING, then CONNECTED_IDLE or RECONNECTING
        }

        while (running.get()) {
            // --- MODIFIED Reconnection and Disconnected Logic at the start of the loop ---
            ClientState cs = currentState.get(); // Get current state once per loop

            if (!connected.get()) {
                if (cs == ClientState.RECONNECTING) {
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        // Automatic reconnect attempt is handled by handleAutomaticReconnectAttempt
                        // which includes the sleep and then calls attemptConnect.
                        // We don't want to block the main input loop with sleep here directly.
                        // The displayMenuOrPrompt will show "Reconnecting..."
                        // and isInteractiveState will be false, causing a short sleep.
                        // Let handleAutomaticReconnectAttempt be called if a timer/trigger mechanism existed.
                        // For now, the natural flow of attemptConnect failing and setting RECONNECTING
                        // will lead here, and the user might type 'connect' or 'quit'.
                        // To make it *automatic* from the loop, we'd call handleAutomaticReconnectAttempt here.
                        // Let's refine this: if in RECONNECTING, sleep and then try.
                        printToConsole("Attempting to reconnect (" + (reconnectAttempts + 1) + "/" + MAX_RECONNECT_ATTEMPTS + ")...");
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                        } catch (InterruptedException e) {
                            if(!running.get()) break; Thread.currentThread().interrupt();
                        }
                        if(running.get() && !connected.get()) { // Check again before trying
                            reconnectAttempts++;
                            attemptConnect(); // This will set state to CONNECTING
                        }
                        continue; // Skip trying to get input for this iteration
                    } else {
                        log("Max automatic reconnect attempts (" + MAX_RECONNECT_ATTEMPTS + ") reached.");
                        printToConsole("Failed to reconnect automatically. Server may be offline.");
                        currentState.set(ClientState.DISCONNECTED); // Shift to permanently disconnected
                        // reconnectAttempts is reset when user types 'connect' or upon successful connection
                    }
                } else if (cs != ClientState.CONNECTING && cs != ClientState.EXITING) {
                    // If not connected, not connecting, not reconnecting, and not exiting, ensure DISCONNECTED state
                    currentState.set(ClientState.DISCONNECTED);
                }
            }
            // --- END MODIFIED Reconnection Logic ---

            displayMenuOrPromptForCurrentState(); // Display UI based on potentially updated state

            if (isInteractiveState(currentState.get())) {
                String input = "";
                if (consoleScanner != null && consoleScanner.hasNextLine()) {
                    input = consoleScanner.nextLine();
                } else {
                    if (running.get()) {
                        log("Console input stream closed. Shutting down client...");
                        stopClient();
                    }
                    break;
                }
                if (input == null) { if (running.get()) stopClient(); break; }
                processUserInputBasedOnState(input.trim());
            } else {
                // In a non-interactive state (like CONNECTING, RECONNECTING, or waiting for server DTO)
                try {
                    Thread.sleep(200); // Small pause to prevent busy-waiting and allow listener to work
                } catch (InterruptedException e) {
                    if (!running.get()) break;
                    Thread.currentThread().interrupt();
                }
            }
        }
        shutdownClientResources();
        log("Client main loop finished.");
    }

    private boolean isInteractiveState(ClientState state) {
        switch (state) {
            case REQUESTING_CASE_LIST_FOR_HOST:
            case REQUESTING_PUBLIC_GAMES:
            case SENDING_HOST_REQUEST:
            case SENDING_JOIN_PUBLIC_REQUEST:
            case SENDING_JOIN_PRIVATE_REQUEST:
            case ATTEMPTING_FINAL_EXAM:
            case SUBMITTING_EXAM_ANSWER:
            case CONNECTING:
            case RECONNECTING:
                return false; // These states primarily wait for server responses
            default:
                return true;
        }
    }
    private boolean isWaitingState(ClientState state) {
        return !isInteractiveState(state) && state != ClientState.EXITING && state != ClientState.DISCONNECTED;
    }


    private void displayMenuOrPromptForCurrentState() {
        consoleLock.lock();
        try {
            ClientState cs = currentState.get();
            // Only print menu/prompt if consoleScanner is available and state is interactive
            if (consoleScanner == null && isInteractiveState(cs)) {
                return; // Cannot display prompt without scanner for interactive states
            }

            System.out.println(); // Start with a newline for better separation

            switch (cs) {
                case CONNECTED_IDLE:
                    printToConsole("--- Main Menu (Connected as " + playerDisplayId + ") ---");
                    printToConsole("1. Host Game");
                    printToConsole("2. Join Game");
                    printToConsole("3. Change Display Name (use: /setname <new_name>)");
                    printToConsole("4. Quit Client");
                    System.out.print("Enter your choice (1-4): ");
                    break;
                case SELECTING_HOST_TYPE:
                    printToConsole("--- Host Game Options ---");
                    printToConsole("1. Host Public Game");
                    printToConsole("2. Host Private Game");
                    printToConsole("3. Back to Main Menu");
                    System.out.print("Enter your choice (1-3): ");
                    break;
                case SELECTING_HOST_CASE:
                    // Case list is displayed by handleAvailableCases DTO handler
                    System.out.print("Enter case number to host, or 0 to return to Host Options: ");
                    break;
                case HOSTING_LOBBY_WAITING:
                    System.out.print("Waiting for another player... (Type '/c <message>', 'exit lobby' to cancel): ");
                    break;
                case SELECTING_JOIN_TYPE:
                    printToConsole("--- Join Game Options ---");
                    printToConsole("1. Join Public Game");
                    printToConsole("2. Join Private Game");
                    printToConsole("3. Back to Main Menu");
                    System.out.print("Enter your choice (1-3): ");
                    break;
                case VIEWING_PUBLIC_GAMES:
                    // Public game list is displayed by handlePublicGamesList DTO handler
                    System.out.print("Enter game number to join, or 0 to return to Join Options: ");
                    break;
                case ENTERING_PRIVATE_CODE:
                    System.out.print("Enter the private game code (or type 'cancel' to go back): ");
                    break;
                case IN_LOBBY_AWAITING_START:
                    System.out.print("Lobby ready! Host (" + (hostPlayerIdInSession != null ? hostPlayerIdInSession.substring(0,Math.min(8,hostPlayerIdInSession.length()))+".." : "N/A") +
                            ") should type 'start case'. Guests can 'request start case' or '/c <message>'. Type 'exit lobby' to leave: ");
                    break;
                case IN_GAME:
                    String sessionPrefix = (currentSessionId != null && currentSessionId.length() >= 4)
                            ? currentSessionId.substring(0, 4)
                            : (currentSessionId != null ? currentSessionId : "GAME");
                    System.out.print(playerDisplayId + " [" + sessionPrefix + "]> ");
                    break;
                case ANSWERING_FINAL_EXAM_Q: // Only host client should reach this state via server prompt
                    System.out.print("Host, your answer for Q" + currentExamQuestionNumberBeingAnswered + ": ");
                    break;

                // "Waiting" states (no direct input prompt, client waits for server)
                case REQUESTING_CASE_LIST_FOR_HOST:
                case REQUESTING_PUBLIC_GAMES:
                case SENDING_HOST_REQUEST:
                case SENDING_JOIN_PUBLIC_REQUEST:
                case SENDING_JOIN_PRIVATE_REQUEST:
                case ATTEMPTING_FINAL_EXAM:    // Host sent "final exam", waiting for first Question DTO
                case SUBMITTING_EXAM_ANSWER:   // Host sent an answer, waiting for next Question DTO or Result DTO
                    printToConsole("Waiting for server response... (type 'cancel' to go back)");
                    break;

                case VIEWING_EXAM_RESULT: // This state is transient; handleExamResult moves to IN_GAME
                    printToConsole("(Exam results displayed. Now back in game.)"); // Should quickly be replaced by IN_GAME prompt
                    break;

                case CONNECTING:
                    printToConsole("Connecting to server... Please wait.");
                    break;
                case RECONNECTING:
                    // The run() loop manages attempt count and transition to DISCONNECTED.
                    // This prompt is shown while it's still trying within the limit.
                    printToConsole("Attempting to reconnect... (" + (reconnectAttempts) + "/" + MAX_RECONNECT_ATTEMPTS + ") Please wait. (Type 'quit' to stop trying)");
                    break;
                case DISCONNECTED:
                    printToConsole("You are disconnected from the server.");
                    System.out.print("Type 'connect' to try again, or 'quit' to exit: ");
                    break;
                case EXITING:
                    printToConsole("Exiting client...");
                    break;
                default: // Should ideally not be reached if all states are handled
                    System.out.print(playerDisplayId + "[" + cs.name() + "]> "); // Generic prompt
                    break;
            }
        } finally {
            consoleLock.unlock();
        }
    }

    private void processUserInputBasedOnState(String input) {
        ClientState cs = currentState.get();
        log("PROCESS_USER_INPUT: Input='" + input + "', State=" + cs);

        // --- Global commands handled first and return ---
        if (input.equalsIgnoreCase("quit")) {
            handleQuitCommand(cs); return;
        }
        if (input.toLowerCase().startsWith("/setname ")) {
            handleSetNameCommand(input); return;
        }
        if (input.equalsIgnoreCase("cancel") && isWaitingState(cs)) {
            handleCancelWaitingState(cs); return;
        }
        if (cs == ClientState.DISCONNECTED && input.equalsIgnoreCase("connect")) {
            this.reconnectAttempts = 0; // Reset attempts for manual connect
            attemptConnect();
            return;
        }
        if (cs == ClientState.RECONNECTING && !input.equalsIgnoreCase("quit")) {
            return;
        }


        // --- State-Specific Input Handling ---
        switch (cs) {
            case CONNECTED_IDLE:
                handleMainMenuInput(input);
                break;
            case SELECTING_HOST_TYPE:
                handleHostTypeSelection(input);
                break;
            case SELECTING_HOST_CASE:
                handleHostCaseSelection(input);
                break;
            case HOSTING_LOBBY_WAITING:
                handleHostingLobbyInput(input);
                break;
            case SELECTING_JOIN_TYPE:
                handleJoinTypeSelection(input);
                break;
            case VIEWING_PUBLIC_GAMES:
                handlePublicGameSelection(input);
                break;
            case ENTERING_PRIVATE_CODE:
                handlePrivateCodeEntry(input);
                break;

            case IN_LOBBY_AWAITING_START:
            case IN_GAME:
                CommandParserClient.ParsedCommandData parsedData = CommandParserClient.parse(input);
                if (parsedData == null) {
                    printToConsole("Invalid command format.");
                    return; // Exit early if parsing fails
                }

                String commandName = parsedData.commandName;
                Command commandToExecute = null;
                boolean specialCommandHandled = false; // Flag to prevent falling into general factory

                // Role-dependent commands OR commands handled differently in these states
                if (commandName.equals("start case")) {
                    if (cs == ClientState.IN_LOBBY_AWAITING_START) {
                        if (isThisClientTheHost()) {
                            commandToExecute = new StartCaseCommand();
                        } else {
                            printToConsole("Sending request to host to start the case...");
                            commandToExecute = new RequestStartCaseCommand();
                        }
                        specialCommandHandled = true;
                    } else {
                        printToConsole("'start case' can only be used when the lobby is ready for the game to start.");
                        // No command to execute, but don't let it fall to unknown.
                        return; // Or specialCommandHandled = true; and commandToExecute remains null
                    }
                } else if (commandName.equals("initiate final exam") || commandName.equals("final exam")) {
                    if (cs == ClientState.IN_GAME) {
                        if (isThisClientTheHost()) {
                            commandToExecute = new InitiateFinalExamCommand();
                        } else {
                            printToConsole("Sending request to host to start the final exam...");
                            commandToExecute = new RequestInitiateExamCommand();
                        }
                        specialCommandHandled = true;
                    } else {
                        printToConsole("'final exam' can only be used when a game is in progress.");
                        return; // Or specialCommandHandled = true; and commandToExecute remains null
                    }
                }
                // Chat is always processed by processChatOrIngameCommand which calls the factory for chat itself
                // or handles it directly. If chat is handled by factory, no special flag needed here.
                // If processChatOrIngameCommand handles chat then returns, it's fine.
                // The current structure passes all non-special commands to processChatOrIngameCommand,
                // which then calls the factory.

                // If NOT a special host/guest differentiated command, let processChatOrIngameCommand handle it
                // This includes chat AND other general in-game commands via the factory.
                if (!specialCommandHandled) {
                    processChatOrIngameCommand(input); // This will parse again and use factory
                    return; // We are done after this, whether it found a command or not
                }

                // If it WAS a special command (start case / final exam) and commandToExecute was set:
                if (commandToExecute != null) {
                    updateClientStateBeforeSending(commandToExecute);
                    sendToServer(commandToExecute);
                }
                // If specialCommandHandled was true but commandToExecute is null (e.g., guest typed 'start case' in IN_GAME state),
                // an error message was already printed.
                break; // Break from the switch case for IN_LOBBY_AWAITING_START / IN_GAME

            case ANSWERING_FINAL_EXAM_Q:
                handleExamAnswerInput(input);
                break;

            // ... (other states: VIEWING_EXAM_RESULT, DISCONNECTED, RECONNECTING, WAITING states) ...
            case VIEWING_EXAM_RESULT:
                if (!input.isEmpty()) { printToConsole("Returning to game..."); }
                currentState.set(ClientState.IN_GAME); // Already handled by DTO
                break;
            case DISCONNECTED:
            case RECONNECTING:
                if (!input.equalsIgnoreCase("connect")) {
                    printToConsole("You are not connected. Type 'connect' or 'quit'.");
                }
                break;
            case REQUESTING_CASE_LIST_FOR_HOST:
            case REQUESTING_PUBLIC_GAMES:
            case SENDING_HOST_REQUEST:
            case SENDING_JOIN_PUBLIC_REQUEST:
            case SENDING_JOIN_PRIVATE_REQUEST:
            case ATTEMPTING_FINAL_EXAM:
            case SUBMITTING_EXAM_ANSWER:
                if (!input.equalsIgnoreCase("cancel")) {
                    // Waiting... prompt already shown
                }
                break;
            default:
                if (isInteractiveState(cs) && !isChatCommand(input) && !input.isEmpty()) {
                    printToConsole("Command '" + input + "' not applicable in current state: " + cs + ". Type 'help'.");
                }
                break;
        }
    }



    private boolean isChatCommand(String input) {
        String lowerInput = input.toLowerCase();
        return lowerInput.startsWith("/chat ") || lowerInput.startsWith("/c ");
    }

    // --- Specific Input Handlers for Different States ---
    private void handleQuitCommand(ClientState cs) {
        if (currentSessionId != null && (cs == ClientState.IN_GAME || cs == ClientState.IN_LOBBY_AWAITING_START || cs == ClientState.HOSTING_LOBBY_WAITING || cs.name().contains("EXAM"))) {
            sendToServer(new ExitCommand()); // Graceful exit from session
        } else {
            stopClient(); // Exit client directly
        }
    }


    private boolean isThisClientTheHost() {
        boolean result = this.playerId != null && this.hostPlayerIdInSession != null && this.playerId.equals(this.hostPlayerIdInSession);
        log("isThisClientTheHost() check: myId=" + this.playerId + ", sessionHostId=" + this.hostPlayerIdInSession + ", result=" + result);
        return result;
    }


    private void handleSetNameCommand(String input) {
        String newName = input.substring("/setname ".length()).trim(); // Assumes input starts with "/setname "
        if (!newName.isEmpty() && newName.length() < 25) { // Basic validation
            String oldName = this.playerDisplayId;
            this.playerDisplayId = newName; // Update locally immediately for prompt
            printToConsole("Display name changed locally to: " + this.playerDisplayId);

            // Send update to server
            if (connected.get()) {
                sendToServer(new UpdateDisplayNameCommand(new UpdateDisplayNameRequestDTO(newName)));
            } else {
                printToConsole("Not connected to server. Name change is local only for now.");
            }
        } else {
            printToConsole("Invalid display name. Must be 1-24 characters.");
        }
    }

    private void handleCancelWaitingState(ClientState cs) { // cs is the current waiting state
        printToConsole("Cancelling current operation...");
        if (this.preWaitingState != null) {
            currentState.set(this.preWaitingState);
            this.preWaitingState = null; // Clear it after use
        } else {
            // Fallback if preWaitingState wasn't set properly (should be an error condition)
            logError("Cancel called from waiting state " + cs + " but preWaitingState was null. Returning to CONNECTED_IDLE.", null);
            currentState.set(ClientState.CONNECTED_IDLE);
        }
        // TODO: Optionally send a "CancelOperationDTO" to server if the server needs to
        // be aware that the client is no longer waiting for a response to a previous request.
    }

    private void handleMainMenuInput(String input) {
        switch (input) {
            case "1": preWaitingState = ClientState.CONNECTED_IDLE; currentState.set(ClientState.SELECTING_HOST_TYPE); break;
            case "2": preWaitingState = ClientState.CONNECTED_IDLE; currentState.set(ClientState.SELECTING_JOIN_TYPE); break;
            // case "3" (/setname) handled globally
            case "4": stopClient(); break;
            default: printToConsole("Invalid choice for Main Menu."); break;
        }
    }

    private void handleHostTypeSelection(String input) {
        switch (input) {
            case "1": // Host Public
                this.intentToHostPublic = true; // <<< CORRECTLY SET
                this.preWaitingState = ClientState.SELECTING_HOST_TYPE;
                currentState.set(ClientState.REQUESTING_CASE_LIST_FOR_HOST);
                // Send the command; the boolean here is mostly for server-side knowledge if needed.
                // The client relies on its own 'intentToHostPublic' field.
                sendToServer(new RequestCaseListCommand(true));
                break;
            case "2": // Host Private
                this.intentToHostPublic = false; // <<< CORRECTLY SET
                this.preWaitingState = ClientState.SELECTING_HOST_TYPE;
                currentState.set(ClientState.REQUESTING_CASE_LIST_FOR_HOST);
                sendToServer(new RequestCaseListCommand(false));
                break;
            case "3":
                currentState.set(ClientState.CONNECTED_IDLE);
                break;
            default:
                printToConsole("Invalid choice for Host Type.");
                break;
        }
    }
    // Modify RequestCaseListCommand to carry the public/private intent
    // public RequestCaseListCommand(boolean forPublicHosting) { this.forPublicHosting = forPublicHosting; ... }
    // And a getter. GameClient needs a field `private boolean intentToHostPublic;` to store this.

    private void handleHostCaseSelection(String input) {
        if (input.equals("0")) {
            currentState.set(ClientState.SELECTING_HOST_TYPE);
            this.availableCasesCache = null; // Clear cache
            return;
        }
        String selectedTitle = null;
        try {
            int caseNum = Integer.parseInt(input);
            if (availableCasesCache != null && caseNum > 0 && caseNum <= availableCasesCache.size()) {
                selectedTitle = availableCasesCache.get(caseNum - 1).getTitle();
            }
        } catch (NumberFormatException e) {
            if (availableCasesCache != null) {
                for (CaseInfoDTO caseInfo : availableCasesCache) {
                    if (caseInfo.getTitle().equalsIgnoreCase(input)) {
                        selectedTitle = caseInfo.getTitle();
                        break;
                    }
                }
            }
        }

        if (selectedTitle != null) {
            // *** Use the stored client-side intent ***
            boolean isActualPublicRequest = this.intentToHostPublic;

            printToConsole("Selected case: " + selectedTitle + ". Creating " +
                    (isActualPublicRequest ? "public" : "private") + " game...");

            this.preWaitingState = ClientState.SELECTING_HOST_CASE;
            currentState.set(ClientState.SENDING_HOST_REQUEST);
            sendToServer(new HostGameCommand(new HostGameRequestDTO(selectedTitle, isActualPublicRequest)));
            this.availableCasesCache = null; // Clear cache
        } else {
            printToConsole("Invalid case selection. Enter a number from the list or exact title.");
            // Stay in SELECTING_HOST_CASE state for another attempt
        }
    }

    private boolean intentToHostPublic; // Add this field to GameClient

    private void handleJoinTypeSelection(String input) {
        switch (input) {
            case "1": // Join Public
                preWaitingState = ClientState.SELECTING_JOIN_TYPE;
                currentState.set(ClientState.REQUESTING_PUBLIC_GAMES);
                sendToServer(new ListPublicGamesCommand());
                break;
            case "2": // Join Private
                preWaitingState = ClientState.SELECTING_JOIN_TYPE;
                currentState.set(ClientState.ENTERING_PRIVATE_CODE);
                break;
            case "3": currentState.set(ClientState.CONNECTED_IDLE); break;
            default: printToConsole("Invalid choice for Join Type."); break;
        }
    }

    private void handlePublicGameSelection(String input) {
        if (input.equals("0")) { // Go back or refresh
            currentState.set(ClientState.SELECTING_JOIN_TYPE);
            this.publicGamesCache = null;
            return;
        }
        String sessionIdToJoin = null;
        try {
            int gameNum = Integer.parseInt(input);
            if (publicGamesCache != null && gameNum > 0 && gameNum <= publicGamesCache.size()) {
                sessionIdToJoin = publicGamesCache.get(gameNum - 1).getSessionId();
            }
        } catch (NumberFormatException e) {
            sessionIdToJoin = input; // Assume it's a direct session ID string
        }

        if (sessionIdToJoin != null && !sessionIdToJoin.isEmpty()){
            printToConsole("Attempting to join public game: " + sessionIdToJoin);
            preWaitingState = ClientState.VIEWING_PUBLIC_GAMES;
            currentState.set(ClientState.SENDING_JOIN_PUBLIC_REQUEST);
            sendToServer(new JoinPublicGameCommand(new JoinPublicGameRequestDTO(sessionIdToJoin)));
            this.publicGamesCache = null;
        } else {
            printToConsole("Invalid selection or session ID.");
        }
    }

    private void handlePrivateCodeEntry(String inputCode) {
        if (inputCode.equalsIgnoreCase("cancel")) {
            currentState.set(ClientState.SELECTING_JOIN_TYPE);
            return;
        }
        // Basic validation, server does the real check
        if (inputCode.length() < 3 || inputCode.length() > 10 ) { // Relaxed validation client side
            printToConsole("Game code seems invalid. Try again or type 'cancel'.");
            return;
        }
        printToConsole("Attempting to join private game with code: " + inputCode);
        preWaitingState = ClientState.ENTERING_PRIVATE_CODE;
        currentState.set(ClientState.SENDING_JOIN_PRIVATE_REQUEST);
        sendToServer(new JoinPrivateGameCommand(new JoinPrivateGameRequestDTO(inputCode.toUpperCase())));
    }

    private void handleHostingLobbyInput(String input){
        if (input.equalsIgnoreCase("exit lobby")) {
            printToConsole("Cancelling hosted game lobby...");
            sendToServer(new ExitCommand()); // Server will end the session
            // Server should send ReturnToLobbyDTO which will change state
            // For immediate feedback:
            // currentState.set(ClientState.CONNECTED_IDLE);
            // this.currentSessionId = null;
        } else {
            processChatOrIngameCommand(input); // Allows chat
        }
    }

    private void handleInLobbyAwaitingStartInput(String input){
        // This state is now primarily handled by the IN_LOBBY_AWAITING_START case
        // in the main switch of processUserInputBasedOnState, which includes
        // host/guest differentiation for 'start case'.
        // If specific commands ONLY for this state (other than start case/chat/exit)
        // are needed, they can be added here.
        // For now, rely on the main switch logic.
        // If input was not 'start case' or chat (which are handled above):
        if (!input.equalsIgnoreCase("start case") && !isChatCommand(input) && !input.equalsIgnoreCase("exit lobby") && !input.equalsIgnoreCase("request start case")) {
            printToConsole("In lobby: Type 'start case' (if host), 'request start case' (if guest), '/c <message>', or 'exit lobby'.");
        }
        // The actual command processing for 'start case'/'request start case' happens in the main switch block.
    }

    // Inside client.GameClient.java
    private void handleExamAnswerInput(String inputAnswer) { // inputAnswer is what the user typed, e.g., "sad"
        if (inputAnswer.isEmpty()) {
            printToConsole("Answer cannot be empty. Please provide an answer.");
            return;
        }
        if (currentExamQuestionNumberBeingAnswered <= 0) { // Safety check
            logError("Cannot submit answer, currentExamQuestionNumberBeingAnswered is invalid: " + currentExamQuestionNumberBeingAnswered, null);
            printToConsole("Error: Not currently expecting an answer for a specific question.");
            currentState.set(ClientState.IN_GAME); // Revert state
            return;
        }

        // Directly create the command
        Command submitCmd = new SubmitExamAnswerCommand(currentExamQuestionNumberBeingAnswered, inputAnswer); // <<< This line seems fine

        // updateClientStateBeforeSending should still be called
        updateClientStateBeforeSending(submitCmd); // Sets state to SUBMITTING_EXAM_ANSWER
        sendToServer(submitCmd);
    }


    private void processChatOrIngameCommand(String input) {
        // (Same as before)
        if (input.toLowerCase().startsWith("/chat ") || input.toLowerCase().startsWith("/c ")) {
            String chatText = input.substring(input.indexOf(" ") + 1).trim();
            if (!chatText.isEmpty()) {
                ChatMessage chatMsg = new ChatMessage(this.playerDisplayId, chatText, System.currentTimeMillis());
                sendToServer(chatMsg);
            } else {
                printToConsole("Usage: /chat <message>  OR  /c <message>");
            }
            return;
        }

        CommandParserClient.ParsedCommandData parsedData = CommandParserClient.parse(input);
        if (parsedData == null) {
            printToConsole("Invalid command format.");
            return;
        }
        Command command = CommandFactoryClient.createCommand(parsedData, currentState.get());
        if (command != null) {
            updateClientStateBeforeSending(command);
            sendToServer(command);
        } else {
            printToConsole("Unknown command: '" + parsedData.commandName + "'. Type 'help'.");
        }
    }

    // --- DTO Handlers (Ensure they update currentState and preWaitingState correctly) ---
// Inside GameClient.java
    private void handleAvailableCases(AvailableCasesDTO ac) {
        if (currentState.get() != ClientState.REQUESTING_CASE_LIST_FOR_HOST) {
            printToConsole("Received case list from server unexpectedly (current state: " + currentState.get() + ").");
            return;
        }
        this.availableCasesCache = ac.getCases();
        if (availableCasesCache.isEmpty()) {
            printToConsole("No cases available on the server to host.");
            currentState.set(ClientState.SELECTING_HOST_TYPE); // Go back
        } else {
            printToConsole("--- Select a Case to Host ---");
            for (int i = 0; i < availableCasesCache.size(); i++) {
                printToConsole((i + 1) + ". " + availableCasesCache.get(i).getTitle());
            }
            // DO NOT change intentToHostPublic here. It was set before the request.
            currentState.set(ClientState.SELECTING_HOST_CASE);
        }
    }

    private void handleTextMessage(TextMessage tm, ClientState stateWhenMessageReceived) {
        String messageToPrint = (tm.isError() ? "[SERVER ERROR] " : "[SERVER] ") + tm.getText();
        printToConsole(messageToPrint);

        // If it's a prompt for the host to answer an exam question
        if (!tm.isError() && tm.getText().startsWith("Host, please submit your answer for Q")) {
            // This client *is the host* and is being prompted to answer.
            // (We assume the server only sends this specific prompt to the actual host player)
            if (isThisClientTheHost()) { // Double check role, though server should only send to host
                currentState.set(ClientState.ANSWERING_FINAL_EXAM_Q);
            } else {
                // Guest received host's prompt - should not happen if server targets correctly.
                log("Warning: Guest client received host-specific exam prompt: " + tm.getText());
            }
        }
        // Reset state if an error message is received while in certain "waiting" states
        else if (tm.isError()) {
            switch (stateWhenMessageReceived) {
                case ATTEMPTING_FINAL_EXAM: // Host tried 'final exam', server rejected it
                case SUBMITTING_EXAM_ANSWER: // Host submitted answer, server sent error instead of next Q/Result
                    printToConsole("Exam process interrupted by server error. Returning to game.");
                    currentState.set(ClientState.IN_GAME);
                    preWaitingState = null;
                    break;
                case SENDING_HOST_REQUEST:
                    printToConsole("Host request failed. Returning to host options.");
                    currentState.set(ClientState.SELECTING_HOST_TYPE);
                    preWaitingState = null;
                    break;
                case SENDING_JOIN_PUBLIC_REQUEST:
                case SENDING_JOIN_PRIVATE_REQUEST:
                    printToConsole("Join request failed. Returning to join options.");
                    currentState.set(ClientState.SELECTING_JOIN_TYPE);
                    preWaitingState = null;
                    break;
                // No need to reset state for REQUESTING_CASE_LIST or REQUESTING_PUBLIC_GAMES
                // as they might get a valid DTO later, or user can 'cancel'.
                // An error TextMessage for those usually means "no cases found" which is informational.
                default:
                    break;
            }
        }
    }

    private void handleChatMessage(ChatMessage cm) {
        printToConsole(cm.toString());
    }
    private void handleRoomDescription(RoomDescriptionDTO rd) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- Location: ").append(rd.getName()).append(" ---\n");
        sb.append(rd.getDescription()).append("\n");
        sb.append("Objects: ").append(rd.getObjectNames().isEmpty() ? "None" : String.join(", ", rd.getObjectNames())).append("\n");
        sb.append("Occupants: ").append(rd.getOccupantNames().isEmpty() ? "None" : String.join(", ", rd.getOccupantNames())).append("\n");
        sb.append("Exits: ");
        if (rd.getExits().isEmpty()) {
            sb.append("None");
        } else {
            rd.getExits().forEach((dir, roomName) -> sb.append(dir).append(" (to ").append(roomName).append("), "));
            if (!rd.getExits().isEmpty()) sb.setLength(sb.length() - 2);
        }
        printToConsole(sb.toString());
        if(currentState.get() != ClientState.IN_GAME) {
            currentState.set(ClientState.IN_GAME);
        }
    }

    private void handleHostGameResponse(HostGameResponseDTO hgr) {

        if (hgr.isSuccess()) {
            this.currentSessionId = hgr.getSessionId();
            printToConsole("Game hosted successfully! Session ID: " + hgr.getSessionId() +
                    (hgr.getGameCode() != null ? ". Private Code for others: " + hgr.getGameCode() : ". This is a public game."));
            printToConsole("Waiting for another player to join...");
            currentState.set(ClientState.HOSTING_LOBBY_WAITING);
        } else {
            printToConsole("Failed to host game: " + hgr.getMessage());
            // Go back to a state where they can try hosting again or choose something else.
            // SELECTING_HOST_CASE is good if they were picking a case.
            // If the failure was very early (e.g. server rejected host due to load),
            // SELECTING_HOST_TYPE or CONNECTED_IDLE might be better.
            // For now, SELECTING_HOST_CASE allows re-picking or cancelling from case selection.
            if (preWaitingState == ClientState.SELECTING_HOST_CASE || currentState.get() == ClientState.SENDING_HOST_REQUEST) {
                currentState.set(ClientState.SELECTING_HOST_CASE);
                // If availableCasesCache is null here, they might need to request list again.
                // This implies that when they select a case in handleHostCaseSelection,
                // if they need to go "back", they should go back to SELECTING_HOST_TYPE.
            } else {
                currentState.set(ClientState.SELECTING_HOST_TYPE); // More general fallback
            }
        }
    }
    private void handlePublicGamesList(PublicGamesListDTO pgl) {
        if (currentState.get() != ClientState.REQUESTING_PUBLIC_GAMES) { printToConsole("Received public games list unexpectedly."); return; }
        this.publicGamesCache = pgl.getGames();
        if (publicGamesCache.isEmpty()) {
            printToConsole("No public games available to join right now.");
            currentState.set(ClientState.SELECTING_JOIN_TYPE); // Go back
        } else {
            printToConsole("--- Available Public Games ---");
            for (int i = 0; i < publicGamesCache.size(); i++) {
                PublicGameInfoDTO gameInfo = publicGamesCache.get(i);
                printToConsole((i + 1) + ". Hosted by: " + gameInfo.getHostPlayerDisplayId() +
                        " | Case: " + gameInfo.getCaseTitle() +
                        " (ID: " + gameInfo.getSessionId().substring(0,Math.min(8, gameInfo.getSessionId().length()))+"..)");
            }
            currentState.set(ClientState.VIEWING_PUBLIC_GAMES);
        }
    }
    private void handleJoinGameResponse(JoinGameResponseDTO jgr) {
        if (jgr.isSuccess()) {
            this.currentSessionId = jgr.getSessionId();
            printToConsole("Successfully joined game session: " + jgr.getSessionId() + ". " + jgr.getMessage());
            // Server will typically follow up with LobbyUpdate and CaseInvitation messages
            currentState.set(ClientState.IN_LOBBY_AWAITING_START);
        } else {
            printToConsole("Failed to join game: " + jgr.getMessage());
            // Determine the best "back" state
            if (preWaitingState == ClientState.VIEWING_PUBLIC_GAMES || currentState.get() == ClientState.SENDING_JOIN_PUBLIC_REQUEST) {
                currentState.set(ClientState.VIEWING_PUBLIC_GAMES); // Let them try another public game
                // Re-requesting the list might be good: sendToServer(new ListPublicGamesCommand());
            } else if (preWaitingState == ClientState.ENTERING_PRIVATE_CODE || currentState.get() == ClientState.SENDING_JOIN_PRIVATE_REQUEST) {
                currentState.set(ClientState.ENTERING_PRIVATE_CODE); // Let them re-enter code
            } else {
                currentState.set(ClientState.SELECTING_JOIN_TYPE); // More general fallback
            }
        }
    }



    private void handleLobbyUpdate(LobbyUpdateDTO lu) {
        printToConsole("[LOBBY UPDATE] " + lu.getMessage());
        if (!lu.getPlayerDisplayIdsInLobbyOrGame().isEmpty()) {
            printToConsole("Players now in session: " + String.join(", ", lu.getPlayerDisplayIdsInLobbyOrGame()));
            // *** THIS IS WHERE THE HOST ID SHOULD BE SET ***
            if (lu.getHostPlayerId() != null) {
                this.hostPlayerIdInSession = lu.getHostPlayerId();
                log("Host ID for current session set to: " + this.hostPlayerIdInSession);
                if (isThisClientTheHost()) {
                    printToConsole("(You are the HOST of this session)");
                } else {
                    printToConsole("(You are a GUEST in this session. Host is: " + this.hostPlayerIdInSession + ")");
                }
            } else {
                log("Warning: LobbyUpdateDTO received without a hostPlayerId.");
                // If hostPlayerId is null, isThisClientTheHost() will likely return false,
                // which might be okay if the session isn't fully formed yet.
            }
        }
        // ... rest of handleLobbyUpdate logic for gameStarting flag ...
        if (lu.isGameStarting()) { // Server signals game is truly ready for 'start case'
            if (currentState.get() != ClientState.IN_LOBBY_AWAITING_START) {
                printToConsole("The game session is now ready for the host to type 'start case'.");
                currentState.set(ClientState.IN_LOBBY_AWAITING_START);
            }
        } else if (currentState.get() == ClientState.HOSTING_LOBBY_WAITING &&
                lu.getPlayerDisplayIdsInLobbyOrGame().size() >= NetworkConstants.MAX_PLAYERS_PER_GAME) {
            // This means P2 joined the host's lobby
            if (isThisClientTheHost()) {
                printToConsole("Your opponent has joined. As host, type 'start case' to begin.");
            } else {
                printToConsole("Lobby is full. Waiting for host to start the case.");
            }
            currentState.set(ClientState.IN_LOBBY_AWAITING_START);
        }
    }

    private void handleExamQuestion(ExamQuestionDTO eq) {
        // Both host and guest will receive this DTO if server broadcasts it.
        consoleLock.lock();
        try {
            printToConsole("\n--- FINAL EXAM QUESTION " + eq.getQuestionNumber() + " ---");
            printToConsole(eq.getQuestionText());
            // Store the question number locally. The HOST client uses this when the server prompts for an answer.
            this.currentExamQuestionNumberBeingAnswered = eq.getQuestionNumber();
            // DO NOT change state to ANSWERING_FINAL_EXAM_Q here for all clients.
            // Only the HOST client will transition to that state upon receiving a specific TextMessage prompt.
            // Guest remains in IN_GAME (or similar) and just sees the question.
        } finally {
            consoleLock.unlock();
        }
    }


    private void handleExamResult(ExamResultDTO er) {
        // Both host and guest will receive this.
        // Guest might be in IN_GAME or IN_LOBBY_AWAITING_START if host started exam very quickly.
        // Host was in SUBMITTING_EXAM_ANSWER.

        consoleLock.lock(); // Ensure all parts print together
        try {
            printToConsole("\n--- FINAL EXAM RESULT ---");
            // ExamResultDTO.toString() already formats score, rank, feedback, and incorrect answers.
            printToConsole(er.toString());
            printToConsole("[SERVER] --- Final Exam Concluded ---");

            // Transition both players back to IN_GAME to continue investigating or exit.
            // Player can then choose to type 'look' to refresh view or 'exit' to leave.
            if (currentSessionId != null) { // Only if still in a session
                currentState.set(ClientState.IN_GAME);
                log("Exam concluded. Client state set to IN_GAME. You can continue investigating or type 'exit'.");
                printToConsole("You can now continue investigating or type 'exit' to leave the game.");
            } else {
                // If session somehow ended, go to idle
                currentState.set(ClientState.CONNECTED_IDLE);
            }
        } finally {
            consoleLock.unlock();
        }
    }
    private void handleReturnToLobby(ReturnToLobbyDTO rtl) {
        consoleLock.lock();
        try {
            printToConsole("[SERVER] " + rtl.getMessage()); // Prints "You have exited the game."
            this.currentSessionId = null;
            this.availableCasesCache = null;
            this.publicGamesCache = null;
            this.hostPlayerIdInSession = null; // Also clear who the host was
            this.intentToHostPublic = true; // Reset intent
            this.currentExamQuestionNumberBeingAnswered = -1; // Reset exam progress

            currentState.set(ClientState.CONNECTED_IDLE); // <<< CRUCIAL STATE CHANGE
            log("Received ReturnToLobbyDTO. Client state set to CONNECTED_IDLE.");
            // The main loop will now naturally call displayMenuOrPromptForCurrentState
            // in its next iteration, which will show the main menu.
        } finally {
            consoleLock.unlock();
        }
    }
    private void processServerMessage(Object message) { // Keep this method, DTO handlers are separate
        consoleLock.lock();
        try {
            ClientState previousStateForErrorCheck = currentState.get();
            if (message instanceof TextMessage) {
                handleTextMessage((TextMessage) message, previousStateForErrorCheck);
            } else if (message instanceof ChatMessage) {
                handleChatMessage((ChatMessage) message);
            }
            else if (message instanceof RoomDescriptionDTO) handleRoomDescription((RoomDescriptionDTO) message);
            else if (message instanceof AvailableCasesDTO) handleAvailableCases((AvailableCasesDTO) message);
            else if (message instanceof HostGameResponseDTO) handleHostGameResponse((HostGameResponseDTO) message);
            else if (message instanceof PublicGamesListDTO) handlePublicGamesList((PublicGamesListDTO) message);
            else if (message instanceof JoinGameResponseDTO) handleJoinGameResponse((JoinGameResponseDTO) message);
            else if (message instanceof LobbyUpdateDTO) handleLobbyUpdate((LobbyUpdateDTO) message);
            else if (message instanceof JournalEntryDTO) printToConsole("[JOURNAL UPDATE] " + message.toString());
            else if (message instanceof ExamQuestionDTO) { // This is the expected success DTO for InitiateFinalExam
                handleExamQuestion((ExamQuestionDTO) message);
            }
            else if (message instanceof PlayerNameChangedDTO) {
                handlePlayerNameChanged((PlayerNameChangedDTO) message);
            }
            else if (message instanceof ExamResultDTO) handleExamResult((ExamResultDTO) message);
            else if (message instanceof ReturnToLobbyDTO) handleReturnToLobby((ReturnToLobbyDTO) message);
            else if (message instanceof NpcMovedDTO) handleNpcMoved((NpcMovedDTO) message);
            else if (message instanceof ClientIdAssignmentDTO) {
                ClientIdAssignmentDTO idDto = (ClientIdAssignmentDTO) message;
                this.playerId = idDto.getPlayerId();
                this.playerDisplayId = idDto.getAssignedDisplayId();
                printToConsole("Server registration complete. Your Player ID: " + this.playerId + ", Display Name: " + this.playerDisplayId);
            } else {
                printToConsole("[UNHANDLED DTO] " + message.getClass().getSimpleName());
            }
        } finally {
            consoleLock.unlock();
        }
    }
    private void handlePlayerNameChanged(PlayerNameChangedDTO pnc) {
        printToConsole("[INFO] " + pnc.toString()); // Uses DTO's toString
        // If this client is the one whose name changed, its local playerDisplayId
        // should already be updated by handleSetNameCommand. This DTO acts as server confirmation
        // and informs other clients.
        // If you maintain a list of other players' display names, update it here.
        if (pnc.getPlayerId().equals(this.playerId)) {
            this.playerDisplayId = pnc.getNewDisplayName(); // Ensure local matches server's confirmed name
            log("My display name confirmed/updated by server to: " + this.playerDisplayId);
        } else {
            // Another player's name changed.
            // If you cache other players' display names, update them here.
            // For now, the DTO's toString() message is the primary feedback.
        }
    }


    private void updateClientStateBeforeSending(Command command) {
        // Only set preWaitingState if transitioning TO a waiting state
        ClientState current = currentState.get();
        ClientState nextState = current; // Default to no change

        if (command instanceof RequestCaseListCommand) nextState = ClientState.REQUESTING_CASE_LIST_FOR_HOST;
        else if (command instanceof HostGameCommand) nextState = ClientState.SENDING_HOST_REQUEST;
        else if (command instanceof ListPublicGamesCommand) nextState = ClientState.REQUESTING_PUBLIC_GAMES;
        else if (command instanceof JoinPublicGameCommand) nextState = ClientState.SENDING_JOIN_PUBLIC_REQUEST;
        else if (command instanceof JoinPrivateGameCommand) nextState = ClientState.SENDING_JOIN_PRIVATE_REQUEST;
        else if (command instanceof InitiateFinalExamCommand) nextState = ClientState.ATTEMPTING_FINAL_EXAM;
        else if (command instanceof SubmitExamAnswerCommand) nextState = ClientState.SUBMITTING_EXAM_ANSWER;

        if (nextState != current && isWaitingState(nextState)) { // If changing to a waiting state
            this.preWaitingState = current; // Store the state we are coming FROM
            currentState.set(nextState);
        } else if (nextState != current) { // If changing to a non-waiting state, or from non-waiting to non-waiting
            currentState.set(nextState);
            this.preWaitingState = null; // No longer relevant
        }
        // If nextState is same as current, no change needed to preWaitingState or currentState
    }

    private void attemptConnect() {
        // We are about to attempt connection, so reset manual attempts counter if called by user command
        // If called by auto-reconnect, the counter is managed by the run loop.
        // If called by user 'connect' command, reset is good:
        // if (currentState.get() == ClientState.DISCONNECTED) {
        //    reconnectAttempts = 0;
        // }

        if (connected.get() || currentState.get() == ClientState.CONNECTING) {
            return; // Already connected or in the process of connecting
        }

        currentState.set(ClientState.CONNECTING);
        // Log message moved here to avoid duplicate if called rapidly
        log("Attempting to connect to server at " + host + ":" + port + " (Attempt " + (reconnectAttempts + 1) + ")");


        try {
            channel = SocketChannel.open();
            channel.configureBlocking(true); // Connect is blocking
            channel.connect(new InetSocketAddress(host, port));
            // For a dedicated listener thread, keeping channel blocking for reads is simpler.
            // channel.configureBlocking(true); // If listener does blocking reads

            connected.set(true);
            reconnectAttempts = 0; // Reset attempts on successful connection
            currentState.set(ClientState.CONNECTED_IDLE); // Transition to main menu state
            log("Successfully connected to the server!");

            // Start network listener thread only on successful connection
            if (networkListenerThread == null || !networkListenerThread.isAlive()) {
                networkListenerThread = new Thread(this::listenToServer, "GameClient-NetworkListener");
                networkListenerThread.setDaemon(true);
                networkListenerThread.start();
            }
            // displayInitialMenu(); // Let the main run loop handle displaying the menu via displayMenuOrPromptForCurrentState

        } catch (ConnectException e) {
            log("Connection refused by server at " + host + ":" + port + ". Server might be down.");
            // handleDisconnect will set state to RECONNECTING (if auto-reconnecting) or DISCONNECTED
            handleDisconnect(ClientState.RECONNECTING, "Connection refused by server");
        } catch (IOException e) {
            logError("IOException during connection attempt: " + e.getMessage(), e);
            handleDisconnect(ClientState.RECONNECTING, "I/O error during connection");
        }
    }

    private void listenToServer() {
        log("Network listener started.");
        try {
            while (running.get() && connected.get() && channel != null && channel.isOpen()) {
                Object receivedObject = SerializationUtils.readFramedObject(channel);
                if (receivedObject != null) {
                    processServerMessage(receivedObject);
                } else {
                    if (connected.get()) {
                        log("Server closed the connection (EOF).");
                        handleDisconnect(ClientState.RECONNECTING, "Server closed connection");
                    }
                    break;
                }
            }
        } catch (IOException e) {
            if (running.get() && connected.get()) {
                logError("IOException in network listener: " + e.getMessage(), null);
                handleDisconnect(ClientState.RECONNECTING, "Network I/O error");
            }
        } catch (ClassNotFoundException e) {
            logError("Error deserializing object: " + e.getMessage(), e);
        } catch (Exception e) {
            if (running.get() && connected.get()) {
                logError("Unexpected error in network listener: " + e.getMessage(), e);
                handleDisconnect(ClientState.RECONNECTING, "Unexpected listener error");
            }
        } finally {
            log("Network listener thread stopped.");
            if (running.get() && connected.get()) {
                handleDisconnect(ClientState.RECONNECTING, "Listener terminated unexpectedly");
            }
        }
    }

    private void sendToServer(Serializable object) {
        if (!connected.get() || channel == null || !channel.isOpen()) {
            printToConsole("Not connected to server. Cannot send message. Type 'connect' to try again.");
            return;
        }
        try {
            SerializationUtils.writeFramedObject(channel, object);
        } catch (IOException e) {
            logError("Error sending message to server: " + e.getMessage(), null);
            handleDisconnect(ClientState.RECONNECTING, "Send I/O error");
        }
    }

    private void handleDisconnect(ClientState newStateAfterDisconnect, String reason) {
        boolean wasConnected = connected.getAndSet(false);
        ClientState oldState = currentState.getAndSet(newStateAfterDisconnect);

        // Log and print user message only if there was a change or significant event
        if (wasConnected) { // If we were actually connected and then lost it
            log("Disconnected from server. Reason: " + reason + ". Old state: " + oldState);
            printToConsole("\nConnection to server lost: " + reason);
        } else if (oldState == ClientState.CONNECTING) { // If an initial connection attempt failed
            log("Initial connection attempt failed. Reason: " + reason + ".");
            printToConsole("\nFailed to connect to server: " + reason);
        }
        // If oldState was already RECONNECTING or DISCONNECTED, further user messages might be redundant here.

        this.currentSessionId = null;
        this.availableCasesCache = null;
        this.publicGamesCache = null;
        this.hostPlayerIdInSession = null; // Clear session-specific data

        if (channel != null && channel.isOpen()) {
            try { channel.close(); } catch (IOException e) { logError("IOException closing channel on disconnect", null); }
        }
        channel = null;

        // Stop the listener thread if it's running
        if (networkListenerThread != null && networkListenerThread.isAlive()) {
            networkListenerThread.interrupt(); // Signal it to stop
            try {
                networkListenerThread.join(100); // Brief wait for it to die
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        networkListenerThread = null;

        // The run() loop will now handle the logic for RECONNECTING or displaying DISCONNECTED prompt.
        // No explicit "Attempting to reconnect..." print here.
    }

    private void handleAutomaticReconnectAttempt() {
        if (!running.get() || currentState.get() == ClientState.CONNECTING) return;
        if (currentState.get() == ClientState.DISCONNECTED || currentState.get() == ClientState.RECONNECTING) {
            log("Automatic reconnect attempt...");
            // printToConsole("Attempting to reconnect to the server..."); // Can be noisy
            try {
                Thread.sleep(3000); // Shorter wait for auto-reconnect
            } catch (InterruptedException e) {
                if (!running.get()) return;
                Thread.currentThread().interrupt();
            }
            if (running.get() && !connected.get()) {
                attemptConnect();
            }
        }
    }

    public void stopClient() {
        log("Client stop requested.");
        running.set(false);
        if (networkListenerThread != null) networkListenerThread.interrupt();
        if (consoleScanner != null) {
            // Closing System.in scanner can be problematic if app tries to restart parts.
            // For a full exit, it's okay, but often left to JVM.
            // If you must close it, ensure it's the absolute end.
        }
        if (channel != null && channel.isOpen()) {
            try { channel.close(); } catch (IOException e) { logError("Exception closing channel on stop", null); }
        }
        connected.set(false);
        currentState.set(ClientState.EXITING);
        printToConsole("Exiting client...");
    }

    private void shutdownClientResources() {
        if (networkListenerThread != null) {
            try { networkListenerThread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        log("Client resources shut down.");
    }

    private void log(String message) {
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + " CLIENT] " + message);
    }
    private void logError(String message, Throwable t) {
        System.err.println("[" + LocalTime.now().format(TIME_FORMATTER) + " CLIENT ERROR] " + message);
        if (t != null && !(t instanceof ConnectException)) { t.printStackTrace(System.err); }
    }
    private void printToConsole(String message) {
        consoleLock.lock();
        try { System.out.println(message); }
        finally { consoleLock.unlock(); }
    }

    private void handleNpcMoved(NpcMovedDTO nmd) {
        // You can make this message more subtle if desired, e.g., not starting with [GAME INFO]
        // if it's considered part of normal world updates.
        printToConsole("[GAME INFO] " + nmd.toString()); // Uses the DTO's helpful toString() method
    }
}