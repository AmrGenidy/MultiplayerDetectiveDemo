package server;

import common.dto.GameStateData; // The DTO that holds all saveable game state
import common.SerializationUtils; // For basic object serialization to bytes

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class PersistenceManager {
    private final String saveDirectory;
    private final GameServer server; // For logging

    public PersistenceManager(String saveDirectory, GameServer server) {
        this.server = server;
        this.saveDirectory = saveDirectory;
        File dir = new File(saveDirectory);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                server.log("Save directory created: " + saveDirectory);
            } else {
                server.log("Error: Could not create save directory: " + saveDirectory);
                // Potentially throw an exception or handle this more gracefully
            }
        } else if (!dir.isDirectory()) {
            server.log("Error: Save path exists but is not a directory: " + saveDirectory);
            // Handle error
        }
    }

    /**
     * Saves the game state to a file.
     * The filename is derived from the GameStateData's sessionId.
     *
     * @param gameState The GameStateData object to save.
     * @throws IOException if an I/O error occurs during saving.
     */
    public void saveGame(GameStateData gameState) throws IOException {
        if (gameState == null || gameState.getSessionId() == null || gameState.getSessionId().trim().isEmpty()) {
            throw new IllegalArgumentException("GameStateData or its SessionId cannot be null/empty for saving.");
        }
        String filename = gameState.getSessionId() + ".sav";
        File saveFile = new File(saveDirectory, filename);

        try (FileOutputStream fos = new FileOutputStream(saveFile);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(gameState);
            server.log("Game state saved successfully: " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            server.log("Error saving game state to " + saveFile.getAbsolutePath() + ": " + e.getMessage());
            throw e; // Re-throw to allow caller to handle
        }
    }

    /**
     * Loads game state from a file.
     *
     * @param sessionId The ID of the session to load.
     * @return The loaded GameStateData object, or null if the file doesn't exist or an error occurs.
     */
    public GameStateData loadGame(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            server.log("Error: SessionId cannot be null or empty for loading.");
            return null;
        }
        String filename = sessionId + ".sav";
        File saveFile = new File(saveDirectory, filename);

        if (!saveFile.exists() || !saveFile.isFile()) {
            server.log("No save file found for session ID: " + sessionId + " at " + saveFile.getAbsolutePath());
            return null;
        }

        try (FileInputStream fis = new FileInputStream(saveFile);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            Object loadedObject = ois.readObject();
            if (loadedObject instanceof GameStateData) {
                server.log("Game state loaded successfully: " + saveFile.getAbsolutePath());
                return (GameStateData) loadedObject;
            } else {
                server.log("Error: Loaded file " + saveFile.getAbsolutePath() + " does not contain valid GameStateData.");
                return null;
            }
        } catch (IOException | ClassNotFoundException e) {
            server.log("Error loading game state from " + saveFile.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a save file for a given session ID.
     * @param sessionId The ID of the session whose save file should be deleted.
     * @return true if the file was successfully deleted or did not exist, false otherwise.
     */
    public boolean deleteSaveGame(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            server.log("Error: SessionId cannot be null or empty for deleting save data.");
            return false;
        }
        String filename = sessionId + ".sav";
        File saveFile = new File(saveDirectory, filename);

        if (!saveFile.exists()) {
            server.log("No save file to delete for session ID: " + sessionId);
            return true; // Considered success if no file to delete
        }

        if (saveFile.delete()) {
            server.log("Save file deleted successfully: " + saveFile.getAbsolutePath());
            return true;
        } else {
            server.log("Error: Could not delete save file: " + saveFile.getAbsolutePath());
            return false;
        }
    }
}