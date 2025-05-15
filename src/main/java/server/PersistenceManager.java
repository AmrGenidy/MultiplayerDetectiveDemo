package server;

import common.dto.GameStateData;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Objects;

/**
 * PersistenceManager My dedicated class for handling all things saving and loading game progress.
 * It takes GameStateData DTOs and shoves them into files, then reads them back. Simple Java Object
 * Serialization is the magic here.
 */
public class PersistenceManager {
  private final String saveDirectoryPath; // Where do I put all these .sav files?
  private final GameServer
      server; // Need this mainly for logging, so I know what the manager is up to.

  /**
   * Sets up the PersistenceManager. It makes sure the save directory exists, or tries to create it.
   *
   * @param saveDirectoryPath The path (e.g., "saved_games") where save files will live.
   * @param server Reference to the GameServer for logging.
   */
  public PersistenceManager(String saveDirectoryPath, GameServer server) {
    Objects.requireNonNull(saveDirectoryPath, "Save directory path cannot be null.");
    Objects.requireNonNull(server, "GameServer reference cannot be null.");

    this.server = server;
    this.saveDirectoryPath = saveDirectoryPath;
    File dir = new File(this.saveDirectoryPath);

    // Check and create the save directory if it doesn't exist.
    if (!dir.exists()) {
      if (dir.mkdirs()) { // mkdirs() creates parent dirs too, which is handy.
        server.log("Persistence: Save directory created: " + this.saveDirectoryPath);
      } else {
        // This is a problem. If I can't make the dir, I can't save.
        server.logError(
            "Persistence ERROR: Could not create save directory: " + this.saveDirectoryPath, null);
        // Could throw a RuntimeException here to halt server startup if saving is critical.
      }
    } else if (!dir.isDirectory()) {
      // Path exists, but it's a file, not a directory. Also, a problem.
      server.logError(
          "Persistence ERROR: Save path '"
              + this.saveDirectoryPath
              + "' exists but is not a directory.",
          null);
      // Also a candidate for a startup-halting exception.
    }
  }

  /**
   * Saves the given game state to a file. Filename is based on the session ID from GameStateData
   * (e.g., "session123.sav").
   *
   * @param gameState The GameStateData object to serialize and save.
   * @throws IOException if anything goes wrong during file writing/serialization.
   * @throws IllegalArgumentException if gameState or its sessionId is null/empty.
   */
  public void saveGame(GameStateData gameState) throws IOException {
    if (gameState == null
        || gameState.getSessionId() == null
        || gameState.getSessionId().trim().isEmpty()) {
      // Can't save without a session ID for the filename.
      throw new IllegalArgumentException(
          "GameStateData or its SessionId cannot be null/empty for saving.");
    }
    String filename = gameState.getSessionId().trim() + ".sav"; // Consistent naming.
    File saveFile = new File(saveDirectoryPath, filename);

    // Using try-with-resources, so streams close automatically. Neat.
    try (FileOutputStream fos = new FileOutputStream(saveFile);
        ObjectOutputStream oos = new ObjectOutputStream(fos)) {
      oos.writeObject(gameState); // The actual serialization magic.
      server.log("Persistence: Game state saved: " + saveFile.getName());
    } catch (IOException e) {
      // Log the error, but also re-throw it so the caller (GameSessionManager or GameSession)
      // knows something went wrong and can inform the user if needed.
      server.logError(
          "Persistence ERROR: Failed to save game state to " + saveFile.getAbsolutePath(), e);
      throw e;
    }
  }
}
