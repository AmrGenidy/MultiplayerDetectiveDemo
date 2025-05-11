package server;

import common.NetworkConstants;
import common.SerializationUtils; // Assuming this is in 'common'

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID; // For unique player IDs

public class ClientSession {
    private final SocketChannel channel;
    private final String playerId; // Unique ID for this client connection
    private String displayId; // Player's chosen name or default
    private GameSession associatedGameSession; // The game this client is part of, if any

    // Buffers for non-blocking I/O
    private final ByteBuffer readBuffer;
    private final ByteBuffer lengthBuffer; // For reading the length prefix
    private boolean readingLength; // State: true if currently reading the 4-byte length
    private int expectedObjectLength; // Expected length of the current object being read

    private final Queue<Serializable> writeQueue; // DTOs waiting to be sent

    private final GameServer server; // Reference to the main server for logging/callbacks

    public ClientSession(SocketChannel channel, GameServer server) {
        this.channel = channel;
        this.server = server;
        this.playerId = UUID.randomUUID().toString(); // Generate a unique ID
        this.displayId = "Player-" + playerId.substring(0, 4); // Default display ID
        this.readBuffer = ByteBuffer.allocate(NetworkConstants.BUFFER_SIZE);
        this.lengthBuffer = ByteBuffer.allocate(4); // For int length
        this.readingLength = true;
        this.expectedObjectLength = -1;
        this.writeQueue = new LinkedList<>();
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String newDisplayId) { // <<< NEW SETTER
        if (newDisplayId != null && !newDisplayId.trim().isEmpty()) {
            this.displayId = newDisplayId.trim();
            // Log or notify server internally if needed
        }
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public GameSession getAssociatedGameSession() {
        return associatedGameSession;
    }

    public void setAssociatedGameSession(GameSession gameSession) {
        this.associatedGameSession = gameSession;
    }

    /**
     * Queues a DTO to be sent to this client.
     * The actual sending happens during the server's write cycle (OP_WRITE).
     * @param dto The Serializable object to send.
     */
    public void send(Serializable dto) {
        synchronized (writeQueue) {
            writeQueue.offer(dto);
            // It's important to also signal the selector that this channel is interested in OP_WRITE
            // This is typically done by the GameServer after calling send()
            server.registerForWrite(this);
        }
    }

    /**
     * Handles reading data from the client's channel.
     * This implements the length-prefix framing for object deserialization.
     * @throws IOException if an I/O error occurs or the connection is closed.
     */
    public void handleRead() throws IOException {
        int bytesRead;
        try {
            if (readingLength) {
                bytesRead = channel.read(lengthBuffer);
                if (bytesRead == -1) throw new IOException("Client disconnected (EOF while reading length).");
                if (bytesRead == 0) return; // No data available right now

                if (!lengthBuffer.hasRemaining()) { // Read all 4 bytes for length
                    lengthBuffer.flip();
                    expectedObjectLength = lengthBuffer.getInt();
                    lengthBuffer.clear();

                    if (expectedObjectLength <= 0 || expectedObjectLength > NetworkConstants.BUFFER_SIZE * 10) { // Max 80KB obj
                        throw new IOException("Invalid object length received: " + expectedObjectLength);
                    }
                    readingLength = false;
                    readBuffer.clear(); // Prepare buffer for object data
                    readBuffer.limit(expectedObjectLength); // Important: limit buffer to object size
                }
            }

            // If not reading length, then reading object data
            if (!readingLength) {
                bytesRead = channel.read(readBuffer);
                if (bytesRead == -1) throw new IOException("Client disconnected (EOF while reading object data).");
                if (bytesRead == 0) return; // No data available

                if (!readBuffer.hasRemaining()) { // Read all expected object bytes
                    readBuffer.flip();
                    byte[] objectData = new byte[expectedObjectLength];
                    readBuffer.get(objectData);

                    try {
                        Object receivedObject = SerializationUtils.deserialize(objectData);
                        server.processClientMessage(this, receivedObject); // Pass to server for routing
                    } catch (ClassNotFoundException e) {
                        server.log("Error: Class not found during deserialization from client " + playerId + ": " + e.getMessage());
                        // Potentially close connection or send error
                    }

                    // Reset for next message
                    readingLength = true;
                    expectedObjectLength = -1;
                    readBuffer.clear();
                    // No need to re-limit here, it's done when expectedObjectLength is set
                }
            }
        } catch (IOException e) {
            // Pass up to GameServer to handle client disconnection
            throw e;
        }
    }


    /**
     * Handles writing queued DTOs to the client's channel.
     * @throws IOException if an I/O error occurs.
     */
    public void handleWrite() throws IOException {
        synchronized (writeQueue) {
            while (!writeQueue.isEmpty()) {
                Serializable dtoToSend = writeQueue.peek(); // Peek first, don't remove until fully sent
                byte[] objectBytes = SerializationUtils.serialize(dtoToSend);
                int length = objectBytes.length;

                ByteBuffer buffer = ByteBuffer.allocate(4 + length);
                buffer.putInt(length);
                buffer.put(objectBytes);
                buffer.flip();

                while (buffer.hasRemaining()) {
                    int written = channel.write(buffer);
                    if (written == 0) {
                        // Channel buffer is full, can't write more now.
                        // OP_WRITE will be triggered again by selector when ready.
                        return;
                    }
                }
                // If we reach here, the entire DTO was written.
                writeQueue.poll(); // Remove successfully sent DTO from queue
            }
            // If queue is empty, no more interest in OP_WRITE for now
            if (writeQueue.isEmpty()) {
                server.unregisterForWrite(this);
            }
        }
    }


    /**
     * Closes the client's connection.
     */
    public void closeConnection() {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            server.log("Error closing channel for client " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "ClientSession{" +
                "playerId='" + playerId + '\'' +
                ", displayId='" + displayId + '\'' +
                ", associatedGameSessionId=" + (associatedGameSession != null ? associatedGameSession.getSessionId() : "None") +
                '}';
    }
}