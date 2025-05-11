package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage;

public class InitiateFinalExamCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    public InitiateFinalExamCommand() {
        super(true); // Requires case to be started
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        if (!context.canStartFinalExam(getPlayerId())) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("You cannot start the final exam at this time.", true));
            return;
        }

        // Send the initial confirmation message (optional, as context might send its own)
        // context.sendResponseToPlayer(getPlayerId(), new TextMessage("Final exam initiated. Stand by for questions...", false));

        // *** CORRECTED: Call the new interface method ***
        context.startExamProcess(getPlayerId());
        // The context's implementation of startExamProcess (which calls startExamForPlayer)
        // will now handle sending the "Final exam initiated..." message AND the first question DTO.
    }

    @Override
    public String getDescription() {
        return "Initiates the final exam process to solve the case.";
    }
}