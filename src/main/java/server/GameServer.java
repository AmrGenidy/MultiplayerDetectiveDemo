package server;

import common.NetworkConstants;
import common.commands.Command;
import common.dto.ChatMessage;
import common.dto.ClientIdAssignmentDTO;
import common.dto.TextMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameServer This is my main NIO server class. It listens for client connections, manages reads and
 * writes for connected clients, and routes messages. It runs in its own thread.
 */
public class GameServer implements Runnable {

  // --- Fields ---
  private final int port;
  private ServerSocketChannel serverSocketChannel;
  private Selector selector;
  private volatile boolean running = true; // Flag to control the main server loop.

  // Manages all active client connections. SocketChannel -> ClientSession.
  private final Map<SocketChannel, ClientSession> clientSessionsMap;
  // Manages game rooms, lobbies, etc. Protected so ServerMain can access for admin commands.
  protected final GameSessionManager sessionManager;
  // Handles saving/loading game state.
  private final PersistenceManager persistenceManager;

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  // --- Constructor ---
  public GameServer(int port) {
    this.port = port;
    this.clientSessionsMap = new ConcurrentHashMap<>(); // Thread-safe map for client sessions.
    // PersistenceManager needs 'this' (GameServer) for logging.
    this.persistenceManager = new PersistenceManager("saved_games", this);
    // SessionManager needs 'this' (GameServer) and PersistenceManager.
    this.sessionManager = new GameSessionManager(this, this.persistenceManager);
  }

  // --- Server Setup & Control ---

  /**
   * Initializes the server: opens selector, server socket, binds, and registers for accept. This
   * needs to be called before starting the server thread.
   */
  public void startServer() throws IOException {
    this.selector = Selector.open();
    this.serverSocketChannel = ServerSocketChannel.open();
    this.serverSocketChannel.configureBlocking(false); // Non-blocking for selector.
    this.serverSocketChannel.socket().bind(new InetSocketAddress(port));
    this.serverSocketChannel.register(
        selector, SelectionKey.OP_ACCEPT); // Listen for new connections.

    log("Server started on port " + port + ". Waiting for connections...");
  }

  /**
   * Signals the server's main loop to stop running and wakes up the selector. Actual cleanup
   * happens in shutdownServerInternals().
   */
  public void stopServer() {
    log("StopServer called. Signaling server loop to terminate...");
    this.running = false;
    if (this.selector != null) {
      this.selector.wakeup(); // Interrupt selector.select() if it's blocking.
    }
  }

  /**
   * Cleans up server resources when the server is shutting down. Closes channels, selector, and
   * saves games. Called from finally block of run().
   */
  protected void shutdownServerInternals() {
    log("Server is shutting down internals...");
    // Close selector first to stop processing new events.
    if (selector != null && selector.isOpen()) {
      // Close all client channels registered with the selector.
      for (SelectionKey key : selector.keys()) {
        try {
          if (key.channel() != null && key.channel().isOpen()) {
            key.channel().close();
          }
          key.cancel(); // Cancel the key.
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
    // Close the server socket channel.
    if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
      try {
        serverSocketChannel.close();
        log("Server socket channel closed.");
      } catch (IOException e) {
        logError("Error closing server socket channel: " + e.getMessage(), e);
      }
    }
    // Important: Save all active games before the server fully exits.
    if (sessionManager != null) {
      sessionManager.saveAllActiveGames();
    }
    log("Server has shut down internals.");
    // clientSessionsMap will be cleared as clients get cleaned up.
  }

  // --- Main Server Loop (Runnable Implementation) ---
  @Override
  public void run() {
    try {
      while (running) {
        // Wait for an event or timeout.
        int readyChannels = selector.select(NetworkConstants.SELECTOR_TIMEOUT);

        if (!running) { // Double-check running flag after select().
          break;
        }
        if (readyChannels == 0) {
          // Timeout, no I/O events. Good place for periodic tasks if needed.
          continue;
        }

        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
          SelectionKey key = keyIterator.next();
          keyIterator.remove(); // Must remove the key from the selected set.

          if (!key.isValid()) {
            // Key might have been cancelled (e.g., channel closed).
            // cleanupClient would have been called if so.
            // log("Key is invalid, skipping.");
            continue;
          }

          // Handle I/O events for this key.
          try {
            if (key.isAcceptable()) {
              handleAccept(key);
            }
            if (key.isReadable()) { // Key might become invalid after accept/read/write if channel
              // closes
              handleRead(key);
            }
            if (key.isWritable()) { // Check validity again
              handleWrite(key);
            }
          } catch (CancelledKeyException cke) {
            // This happens if channel closed while processing.
            // ClientSession object might still be in map if cleanupClient hasn't run for this key
            // yet.
            ClientSession client =
                (ClientSession) key.attachment(); // More reliable way to get session
            String clientId =
                (client != null)
                    ? client.getPlayerId()
                    : "Unknown (channel: " + key.channel().hashCode() + ")";
            log("Key cancelled for client " + clientId + " during event processing. Cleaning up.");
            cleanupClient(key, "Key was cancelled during op");
          } catch (IOException e) {
            // Network errors (e.g., client disconnected abruptly).
            ClientSession client = (ClientSession) key.attachment();
            String clientId =
                (client != null)
                    ? client.getPlayerId()
                    : "Unknown (channel: " + key.channel().hashCode() + ")";
            log(
                "I/O Error for client "
                    + clientId
                    + ": "
                    + e.getMessage()
                    + ". Closing connection.");
            cleanupClient(key, "I/O Error: " + e.getMessage());
          } catch (Exception e) {
            // Catch-all for unexpected errors during key processing to keep server running.
            ClientSession client = (ClientSession) key.attachment();
            String clientId = (client != null) ? client.getPlayerId() : "Unknown";
            logError("Unexpected error processing key for client " + clientId, e);
            cleanupClient(key, "Unexpected error: " + e.getMessage());
          }
        }
      }
    } catch (ClosedSelectorException cse) {
      // This is expected if stopServer() closes the selector.
      log("Selector closed, server thread shutting down as expected.");
    } catch (IOException e) {
      // Major I/O error in the selector.select() loop itself.
      logError("Server run loop I/O error (selector.select() issue?): " + e.getMessage(), e);
    } finally {
      // If running is still true, it means loop exited due to an unexpected error,
      // not a clean shutdown signal. So, ensure cleanup.
      if (running) {
        log("Server loop exited unexpectedly. Initiating internal shutdown.");
        shutdownServerInternals();
      }
      // If running is false, shutdownServerInternals() will be called by ServerMain's finally block
      // or the shutdown hook.
    }
  }

  // --- Event Handlers (Called by run() loop) ---

  private void handleAccept(SelectionKey key) throws IOException {
    ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
    SocketChannel clientChannel = ssc.accept(); // Accept the new connection.

    if (clientChannel != null) {
      clientChannel.configureBlocking(false); // Must be non-blocking for selector.
      SelectionKey clientKey =
          clientChannel.register(
              selector, SelectionKey.OP_READ); // Initially interested in reading.

      ClientSession clientSession = new ClientSession(clientChannel, this);
      clientKey.attach(clientSession); // Attach session object to key for easy retrieval.
      clientSessionsMap.put(clientChannel, clientSession); // Also keep in map for now.

      SocketAddress remoteAddr = clientChannel.getRemoteAddress();
      log(
          "Accepted new connection from: "
              + (remoteAddr != null ? remoteAddr.toString() : "Unknown")
              + " | PlayerID: "
              + clientSession.getPlayerId()
              + " (Display: "
              + clientSession.getDisplayId()
              + ")");

      // Send initial DTOs for client setup.
      clientSession.send(
          new ClientIdAssignmentDTO(clientSession.getPlayerId(), clientSession.getDisplayId()));
      clientSession.send(
          new TextMessage(
              "Welcome, " + clientSession.getDisplayId() + "! Connected to Detective Game Server.",
              false));
    }
  }

  private void handleRead(SelectionKey key) throws IOException {
    ClientSession clientSession = (ClientSession) key.attachment(); // Get session from key.
    if (clientSession != null) {
      clientSession.handleRead(); // Delegate to ClientSession to read framed data.
    } else {
      // This shouldn't happen if attach() was successful and key is valid.
      log(
          "Warning: Read event for key with no ClientSession attachment. Channel: "
              + key.channel().hashCode());
      key.channel().close();
      key.cancel();
    }
  }

  private void handleWrite(SelectionKey key) throws IOException {
    ClientSession clientSession = (ClientSession) key.attachment();
    if (clientSession != null) {
      clientSession.handleWrite(); // Delegate to ClientSession to write queued data.
    } else {
      log(
          "Warning: Write event for key with no ClientSession attachment. Channel: "
              + key.channel().hashCode());
      key.channel().close();
      key.cancel();
    }
  }

  // --- Client Management & Message Routing ---

  /**
   * Processes a fully deserialized message from a client. Routes it to GameSessionManager (for
   * lobby commands) or GameSession (for in-game commands).
   */
  public void processClientMessage(ClientSession sender, Object message) {
    if (sender == null || message == null) return; // Basic sanity check.
    log("Received from " + sender.getDisplayId() + ": " + message.getClass().getSimpleName());

    if (message instanceof Command command) {
      GameSession session = sender.getAssociatedGameSession();
      if (session != null) {
        // Client is in a game session (could be WAITING, ACTIVE, etc.)
        session.processCommand(command, sender.getPlayerId());
      } else {
        // Client is not in a game session yet (e.g., just connected, using lobby commands).
        sessionManager.processLobbyCommand(sender, command);
      }
    } else if (message instanceof ChatMessage chatMsg) {
      GameSession session = sender.getAssociatedGameSession();
      if (session != null
          && (session.getState() == GameSessionState.ACTIVE
              || session.getState() == GameSessionState.IN_LOBBY_AWAITING_START
              || (session.getState() == GameSessionState.WAITING_FOR_PLAYERS
                  && session.isFull()))) {
        session.processChatMessage(chatMsg);
      } else {
        sender.send(new TextMessage("Chat only available in game lobbies or active games.", true));
      }
    } else {
      log(
          "Warning: Received unknown object type from "
              + sender.getPlayerId()
              + ": "
              + message.getClass().getName());
      sender.send(new TextMessage("Server received an unknown message type.", true));
    }
  }

  /**
   * Cleans up a client connection: cancels key, closes channel, notifies session manager. Called
   * when an error occurs or client disconnects.
   */
  private void cleanupClient(SelectionKey key, String reason) {
    SocketChannel clientChannel = null;
    ClientSession clientSession = null;

    if (key != null) {
      clientChannel = (SocketChannel) key.channel();
      clientSession = (ClientSession) key.attachment(); // Get session from attachment
      key.cancel(); // Cancel the key with the selector.
    }

    // Remove from map if still using it as primary lookup, though attachment is better.
    if (clientChannel != null) {
      clientSessionsMap.remove(clientChannel);
    }

    try {
      if (clientChannel != null && clientChannel.isOpen()) {
        clientChannel.close(); // Close the network channel.
      }
    } catch (IOException ex) {
      // Log, but don't let this stop cleanup.
      log("Error closing channel during cleanup for reason '" + reason + "': " + ex.getMessage());
    }

    if (clientSession != null) {
      log(
          "Client "
              + clientSession.getDisplayId()
              + " (ID: "
              + clientSession.getPlayerId()
              + ") connection resources cleaned up. Reason: "
              + reason);
      sessionManager.handleClientDisconnect(
          clientSession); // Notify SessionManager to handle game logic.
    } else if (clientChannel != null) {
      // If session was null but channel existed (e.g. error during accept/attach)
      log(
          "Cleaned up a client channel ("
              + clientChannel.hashCode()
              + ") that had no ClientSession attached. Reason: "
              + reason);
    } else {
      log("Cleanup called but key or channel was null. Reason: " + reason);
    }
  }

  /**
   * Called by ClientSession when it has data in its writeQueue. Signals the selector that this
   * channel is interested in write operations.
   */
  public void registerForWrite(ClientSession client) {
    if (client == null
        || client.getChannel() == null
        || !client.getChannel().isOpen()
        || selector == null
        || !selector.isOpen()) {
      // log("Attempted to register for write on invalid client/channel/selector state.");
      return;
    }
    try {
      SelectionKey key = client.getChannel().keyFor(selector);
      if (key != null && key.isValid()) {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); // Add write interest.
        selector.wakeup(); // Wake up selector if it's blocking in select().
      }
    } catch (CancelledKeyException e) {
      // Key got cancelled, client likely disconnected. Cleanup is handled elsewhere.
      // log("Could not register for write, key cancelled for " + client.getPlayerId());
    }
  }

  /**
   * Called by ClientSession when its writeQueue is empty. Signals the selector that this channel is
   * no longer interested in write operations (for now).
   */
  public void unregisterForWrite(ClientSession client) {
    if (client == null
        || client.getChannel() == null
        || !client.getChannel().isOpen()
        || selector == null
        || !selector.isOpen()) {
      return;
    }
    try {
      SelectionKey key = client.getChannel().keyFor(selector);
      if (key != null && key.isValid()) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); // Remove write interest.
      }
    } catch (CancelledKeyException e) {
      // log("Could not unregister for write, key already cancelled for " + client.getPlayerId());
    }
  }

  // --- Logging Utilities ---
  public void log(String message) {
    System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + " SERVER] " + message);
  }

  public void logError(String message, Throwable throwable) {
    System.err.println("[" + LocalTime.now().format(TIME_FORMATTER) + " SERVER ERROR] " + message);
    if (throwable != null) {
      throwable.printStackTrace(System.err); // Keep for now, useful for debugging.
    }
  }
}
