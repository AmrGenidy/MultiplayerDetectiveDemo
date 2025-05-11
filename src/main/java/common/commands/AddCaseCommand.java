package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage; // For sending a response back to the player.

/**
 * AddCaseCommand
 * This command represents the player's intent to add a new case to the game
 * from a specified JSON file path.
 * The actual file loading and validation logic resides in the context
 * (GameContextSinglePlayer for local, or server-side logic for admin).
 */
public class AddCaseCommand extends BaseCommand {
    private static final long serialVersionUID = 1L; // Standard for Serializable classes.

    private final String filePath; // Path to the .json case file.

    /**
     * Constructor for AddCaseCommand.
     * @param filePath The local file system path to the case JSON file.
     *                 Cannot be null or empty.
     */
    public AddCaseCommand(String filePath) {
        super(false); // This command can be used before a case is started.
        if (filePath == null || filePath.trim().isEmpty()) {
            // File path is essential, so throw an error if it's bad.
            throw new IllegalArgumentException("File path cannot be null or empty for AddCaseCommand.");
        }
        this.filePath = filePath.trim();
    }

    /**
     * Gets the file path provided for the new case.
     * @return The file path string.
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Executes the add case logic via the context.
     * The context implementation (SP or Server) determines how to handle this.
     * For a client sending to a server, this would likely be an admin-only feature
     * or the server would need a secure way to access/validate the path.
     */
    @Override
    protected void executeCommandLogic(GameActionContext context) {
        String message = "Server/Game received 'add case' request for path: " + filePath +
                ". Actual processing depends on mode and permissions.";
        context.sendResponseToPlayer(getPlayerId(), new TextMessage(message, false));
    }

    @Override
    public String getDescription() {
        return "Attempts to add a new case from a JSON file. Usage: add case [file_path]";
    }
}