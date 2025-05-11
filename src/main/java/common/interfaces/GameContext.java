package common.interfaces;

import Core.Room; // Assuming Room is in 'core' package
import Core.Suspect; // Assuming Suspect is in 'core' package
import java.util.Map;

/**
 * Defines methods needed by Extractors (server-side and single-player)
 * for loading case data into a game context.
 */
public interface GameContext {
    /**
     * Adds a room to the game context.
     *
     * @param room The Room object to add.
     */
    void addRoom(Room room);

    /**
     * Retrieves a room by its name.
     *
     * @param name The name of the room.
     * @return The Room object if found, null otherwise.
     */
    Room getRoomByName(String name);

    /**
     * Retrieves all rooms currently loaded in the context.
     * Primarily for validation purposes like connectivity checks.
     *
     * @return A map of room names to Room objects.
     */
    Map<String, Room> getAllRooms();

    /**
     * Adds a suspect to the game context.
     *
     * @param suspect The Suspect object to add.
     */
    void addSuspect(Suspect suspect);

    /**
     * Logs a message related to the data loading process.
     *
     * @param message The message to log.
     */
    void logLoadingMessage(String message);

    /**
     * Gets an identifier for the current context, useful for logging.
     * e.g., "Server-Session-XYZ" or "SinglePlayer-Game1".
     *
     * @return A string identifier for the context.
     */
    String getContextIdForLog();
}