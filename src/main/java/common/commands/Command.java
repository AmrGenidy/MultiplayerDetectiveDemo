package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serializable;

public interface Command extends Serializable {
    long serialVersionUID = 1L; // Good practice for Serializable interfaces

    /**
     * Executes the command logic using the provided game context.
     * @param context The GameActionContext to operate on.
     */
    void execute(GameActionContext context);

    /**
     * Gets a user-friendly description of what the command does.
     * @return The command description.
     */
    String getDescription();

    /**
     * Sets the ID of the player issuing this command.
     * This will be set by the server upon receiving the command.
     * @param playerId The player's unique ID.
     */
    void setPlayerId(String playerId);

    /**
     * Gets the ID of the player who issued this command.
     * @return The player's unique ID.
     */
    String getPlayerId();
}