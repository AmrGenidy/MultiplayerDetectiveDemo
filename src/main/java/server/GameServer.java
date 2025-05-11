package server;

import common.NetworkConstants; // For PORT, SELECTOR_TIMEOUT
import common.commands.Command; // For type checking received objects
import common.dto.ChatMessage;  // For type checking received objects
import common.dto.ClientIdAssignmentDTO;
import common.dto.TextMessage;  // For sending error messages

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap; // For thread-safe client map
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class GameServer implements Runnable {
    private final int port;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private volatile boolean running = true;

    private final Map<SocketChannel, ClientSession> clientSessionsMap;
    protected final GameSessionManager sessionManager; // Made protected for ServerMain to access for saveall/reload
    private final PersistenceManager persistenceManager;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public GameServer(int port) {
        this.port = port;
        this.clientSessionsMap = new ConcurrentHashMap<>();
        // Initialize PersistenceManager first as GameSessionManager might need it
        this.persistenceManager = new PersistenceManager("saved_games", this);
        this.sessionManager = new GameSessionManager(this, this.persistenceManager);
    }

    /**
     * Initializes the server socket, selector, and registers for accept operations.
     * @throws IOException if an I/O error occurs during setup.
     */
    public void startServer() throws IOException {
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        log("Server started on port " + port + ". Waiting for connections...");
    }

    @Override
    public void run() {
        try {
            while (running) {
                int readyChannels = selector.select(NetworkConstants.SELECTOR_TIMEOUT);
                if (!running) { // Check after select, as stopServer() might have been called
                    break;
                }
                if (readyChannels == 0) {
                    continue; // No events, loop again
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        log("Key is invalid, skipping.");
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        }
                        if (key.isReadable()) {
                            handleRead(key);
                        }
                        if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (CancelledKeyException cke) {
                        log("Key cancelled for client: " + getClientSessionFromKey(key).getPlayerId() + ". Cleaning up.");
                        cleanupClient(key, "Key was cancelled");
                    } catch (IOException e) {
                        ClientSession client = getClientSessionFromKey(key);
                        String clientId = (client != null) ? client.getPlayerId() : "Unknown (channel: " + key.channel().hashCode() + ")";
                        log("I/O Error for client " + clientId + ": " + e.getMessage() + ". Closing connection.");
                        cleanupClient(key, "I/O Error: " + e.getMessage());
                    } catch (Exception e) { // Catch any other unexpected exceptions per key
                        ClientSession client = getClientSessionFromKey(key);
                        String clientId = (client != null) ? client.getPlayerId() : "Unknown";
                        logError("Unexpected error processing key for client " + clientId + ": " + e.getMessage(), e);
                        cleanupClient(key, "Unexpected error: " + e.getMessage());
                    }
                }
            }
        } catch (ClosedSelectorException cse) {
            log("Selector closed, server thread shutting down.");
        }
        catch (IOException e) {
            logError("Server run loop I/O error: " + e.getMessage(), e);
        } finally {
            // ShutdownServer is called by ServerMain, or if running standalone
            if (running) { // If loop exited for reasons other than stopServer()
                shutdownServerInternals();
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = ssc.accept(); // Accept new connection

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            // Register for read initially. Write interest will be added when there's data to send.
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

            // Create a ClientSession to manage this client's state and I/O
            ClientSession clientSession = new ClientSession(clientChannel, this);
            // Associate the ClientSession with the SelectionKey for easy retrieval
            clientKey.attach(clientSession); // Store ClientSession as an attachment to the key

            // Note: clientSessionsMap is still useful if you need to iterate all sessions
            // or access them by SocketChannel directly for some reason, but attachment is good for key-based retrieval.
            clientSessionsMap.put(clientChannel, clientSession);


            SocketAddress remoteAddr = clientChannel.getRemoteAddress();
            String remoteAddrStr = (remoteAddr != null) ? remoteAddr.toString() : "Unknown address";
            log("Accepted new connection from: " + remoteAddrStr +
                    " | Assigned PlayerID: " + clientSession.getPlayerId() +
                    " (Display: " + clientSession.getDisplayId() + ")");

            // Send the client its assigned ID and confirmed display name
            clientSession.send(new ClientIdAssignmentDTO(clientSession.getPlayerId(), clientSession.getDisplayId()));
            // Send a welcome message
            clientSession.send(new TextMessage("Welcome, " + clientSession.getDisplayId() + "! You are connected to the Detective Game Server.", false));
            // The client upon receiving ClientIdAssignmentDTO will store these values for its own use.
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        ClientSession clientSession = getClientSessionFromKey(key);
        if (clientSession != null) {
            clientSession.handleRead(); // ClientSession handles framed reading and calls processClientMessage
        } else {
            log("Warning: Read event for unknown client channel. Channel hash: " + key.channel().hashCode());
            // This case should ideally not happen if map is managed correctly.
            // If it does, it's safer to close the channel.
            key.channel().close();
            key.cancel();
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        ClientSession clientSession = getClientSessionFromKey(key);
        if (clientSession != null) {
            clientSession.handleWrite(); // ClientSession handles writing queued DTOs
        } else {
            log("Warning: Write event for unknown client channel. Channel hash: " + key.channel().hashCode());
            key.channel().close();
            key.cancel();
        }
    }

    /**
     * Processes a fully deserialized message received from a client.
     * Routes Command objects and ChatMessage objects appropriately.
     * @param sender The ClientSession that sent the message.
     * @param message The deserialized object (Command or ChatMessage).
     */
    public void processClientMessage(ClientSession sender, Object message) {
        if (sender == null || message == null) { /* ... */ return; }
        log("Received from " + sender.getDisplayId() + " (" + sender.getPlayerId() + "): " + message.getClass().getSimpleName());

        if (message instanceof Command) {
            Command command = (Command) message;
            GameSession session = sender.getAssociatedGameSession();

            // --- MODIFIED ROUTING LOGIC ---
            if (session != null) {
                // If client is in ANY session (waiting, active, lobby), let session handle it.
                // Session will decide based on its state and command type.
                session.processCommand(command, sender.getPlayerId());
            } else {
                // Only process as a lobby command if client is NOT in any session.
                sessionManager.processLobbyCommand(sender, command);
            }
            // --- END MODIFICATION ---

        } else if (message instanceof ChatMessage) {
            // ... (chat routing, potentially also always via session if associated) ...
            ChatMessage chatMsg = (ChatMessage) message;
            GameSession session = sender.getAssociatedGameSession();
            if (session != null &&
                    (session.getState() == GameSessionState.ACTIVE ||
                            session.getState() == GameSessionState.IN_LOBBY_AWAITING_START || // Allow chat here
                            session.getState() == GameSessionState.WAITING_FOR_PLAYERS && session.isFull())) { // And here
                session.processChatMessage(chatMsg);
            } else {
                sender.send(new TextMessage("You can only chat once in a game lobby with another player or during a game.", true));
            }
        } else { /* ... unknown object ... */ }
    }

    private ClientSession getClientSessionFromKey(SelectionKey key) {
        return clientSessionsMap.get((SocketChannel) key.channel());
    }

    private void cleanupClient(SelectionKey key, String reason) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession clientSession = clientSessionsMap.remove(clientChannel); // Remove from map first

        if (key != null) { // Key might be null if called directly
            key.cancel();
        }
        try {
            if (clientChannel != null && clientChannel.isOpen()) {
                clientChannel.close();
            }
        } catch (IOException ex) {
            log("Error closing channel during cleanup for reason '" + reason + "': " + ex.getMessage());
        }

        if (clientSession != null) {
            log("Client " + clientSession.getDisplayId() + " (" + clientSession.getPlayerId() + ") connection closed. Reason: " + reason);
            sessionManager.handleClientDisconnect(clientSession); // Notify manager
        } else {
            log("Cleaned up a client channel that had no ClientSession object associated (already removed or error). Reason: " + reason);
        }
    }

    /**
     * Registers the client's channel for write operations.
     * Called by ClientSession when it has data in its writeQueue.
     * @param client The ClientSession that needs to write.
     */
    public void registerForWrite(ClientSession client) {
        if (client == null || client.getChannel() == null || !client.getChannel().isOpen()) {
            log("Attempted to register for write on null or closed client/channel.");
            return;
        }
        try {
            SelectionKey key = client.getChannel().keyFor(selector);
            if (key != null && key.isValid()) {
                // Atomically add OP_WRITE interest
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                selector.wakeup(); // Important: Wake up selector if it's blocking in select()
            } else {
                log("Could not register for write, key is null or invalid for " + client.getPlayerId());
            }
        } catch (CancelledKeyException e) {
            log("Could not register for write, key cancelled for " + client.getPlayerId() + ". Cleaning up.");
            cleanupClient(client.getChannel().keyFor(selector), "Key cancelled while registering for write");
        }
    }

    /**
     * Unregisters the client's channel from write operations.
     * Called by ClientSession when its writeQueue is empty.
     * @param client The ClientSession that no longer needs to write.
     */
    public void unregisterForWrite(ClientSession client) {
        if (client == null || client.getChannel() == null || !client.getChannel().isOpen()) {
            log("Attempted to unregister for write on null or closed client/channel.");
            return;
        }
        try {
            SelectionKey key = client.getChannel().keyFor(selector);
            if (key != null && key.isValid()) {
                // Atomically remove OP_WRITE interest
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            } else {
                log("Could not unregister for write, key is null or invalid for " + client.getPlayerId());
            }
        } catch (CancelledKeyException e) {
            // Key already cancelled, likely client disconnected.
            log("Could not unregister for write, key already cancelled for " + client.getPlayerId());
        }
    }

    /**
     * Signals the server to stop its main loop and begin shutdown.
     */
    public void stopServer() {
        log("StopServer called. Signaling server loop to terminate...");
        this.running = false;
        if (selector != null) {
            selector.wakeup(); // Interrupt the select() call if it's blocking
        }
    }

    /**
     * Performs the actual shutdown of server resources like channels and selector.
     * This is called when the run() loop exits.
     */
    protected void shutdownServerInternals() {
        log("Server is shutting down internals...");
        if (selector != null && selector.isOpen()) {
            for (SelectionKey key : selector.keys()) { // Iterate over a copy if modifying keys
                try {
                    if (key.channel() != null && key.channel().isOpen()) {
                        key.channel().close();
                    }
                    key.cancel();
                } catch (IOException e) {
                    logError("Error closing channel during shutdown: " + key.channel(), e);
                }
            }
            try {
                selector.close();
                log("Selector closed.");
            } catch (IOException e) {
                logError("Error closing selector: " + e.getMessage(), e);
            }
        }
        if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
            try {
                serverSocketChannel.close();
                log("Server socket channel closed.");
            } catch (IOException e) {
                logError("Error closing server socket channel: " + e.getMessage(), e);
            }
        }
        // Save all active games before fully exiting
        if (sessionManager != null) {
            sessionManager.saveAllActiveGames();
        }
        log("Server has shut down internals.");
    }

    // --- Logging Utilities ---
    public void log(String message) {
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + " SERVER] " + message);
    }

    public void logError(String message, Throwable throwable) {
        System.err.println("[" + LocalTime.now().format(TIME_FORMATTER) + " SERVER ERROR] " + message);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }
}