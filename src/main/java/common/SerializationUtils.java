package common;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SerializationUtils {

    private SerializationUtils() {} // Prevent instantiation

    /**
     * Serializes a Serializable object into a byte array.
     *
     * @param object The object to serialize.
     * @return Byte array representing the serialized object, or null on error.
     * @throws IOException if an I/O error occurs during serialization.
     */
    public static byte[] serialize(Serializable object) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            return baos.toByteArray();
        }
    }

    /**
     * Deserializes a byte array back into an object.
     *
     * @param bytes The byte array to deserialize.
     * @return The deserialized object, or null on error.
     * @throws IOException if an I/O error occurs.
     * @throws ClassNotFoundException if the class of a serialized object cannot be found.
     */
    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }

    /**
     * Writes a length-prefixed serialized object to a SocketChannel.
     * This method is blocking for the write operations but assumes the channel is ready.
     * For non-blocking, this would be part of a larger write handling loop.
     *
     * @param channel The SocketChannel to write to.
     * @param object The Serializable object to send.
     * @throws IOException if an I/O error occurs.
     */
    public static void writeFramedObject(SocketChannel channel, Serializable object) throws IOException {
        byte[] objectBytes = serialize(object);
        int length = objectBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(4 + length); // 4 bytes for length (int) + object bytes
        buffer.putInt(length);
        buffer.put(objectBytes);
        buffer.flip(); // Prepare buffer for writing

        while (buffer.hasRemaining()) {
            channel.write(buffer); // This might not write all bytes in one go in non-blocking mode
        }
    }

    /**
     * Reads a length-prefixed serialized object from a SocketChannel.
     * This method attempts to read in a blocking manner for simplicity here.
     * In a true non-blocking server, this would be more complex, involving
     * accumulating bytes in a session-specific buffer until a full frame is received.
     *
     * @param channel The SocketChannel to read from.
     * @return The deserialized object, or null if the end of stream is reached before reading length.
     * @throws IOException if an I/O error occurs.
     * @throws ClassNotFoundException if the class of a serialized object cannot be found.
     */
    public static Object readFramedObject(SocketChannel channel) throws IOException, ClassNotFoundException {
        ByteBuffer lengthBuffer = ByteBuffer.allocate(4); // To read the integer length

        // Read the length of the object
        int bytesRead = 0;
        while (bytesRead < 4) {
            int read = channel.read(lengthBuffer);
            if (read == -1) { // End of stream
                if (bytesRead == 0) return null; // Clean EOF before length
                throw new EOFException("Stream ended prematurely while reading object length.");
            }
            bytesRead += read;
            if (bytesRead < 4 && read == 0) {
                // This can happen in non-blocking mode if no data is available yet.
                // For a simplified blocking read, we might spin or wait.
                // For a true non-blocking server, you'd return and try later.
                // Here, we assume data will arrive or it's an error/EOF.
                try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Read interrupted", e); }
            }
        }
        lengthBuffer.flip();
        int objectLength = lengthBuffer.getInt();

        if (objectLength <= 0 || objectLength > 1024 * 1024 * 10) { // Basic sanity check for length (e.g., max 10MB)
            throw new IOException("Invalid object length received: " + objectLength);
        }

        // Read the object bytes
        ByteBuffer objectBuffer = ByteBuffer.allocate(objectLength);
        bytesRead = 0;
        while (bytesRead < objectLength) {
            int read = channel.read(objectBuffer);
            if (read == -1) {
                throw new EOFException("Stream ended prematurely while reading object data.");
            }
            bytesRead += read;
            if (bytesRead < objectLength && read == 0) {
                try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Read interrupted", e); }
            }
        }
        objectBuffer.flip();

        return deserialize(objectBuffer.array());
    }
}