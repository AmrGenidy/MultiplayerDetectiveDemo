package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage; // Or a more specific DTO for state change

public class ExitCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    public ExitCommand() {
        super(false); // Can be used whether case is started or not.
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // The context.handlePlayerExit(getPlayerId()) will implement the mode-specific logic.
        // For example:
        // context.sendResponseToPlayer(getPlayerId(), new TextMessage("Exiting current context...", false));
        // The actual state transition (to case select, lobby, or app exit) is managed by
        // the specific GameActionContext implementation (SP or Server).
        // Server context might trigger a change in the client's state via a specific DTO.

        // This is a crucial method that GameContextSinglePlayer and GameContextServer
        // will implement differently.
        context.handlePlayerExitRequest(getPlayerId());
    }

    @Override
    public String getDescription() {
        return "Exits the current case (returning to case selection/lobby) or the application if not in a case.";
    }
}