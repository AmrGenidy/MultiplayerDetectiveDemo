package common.commands;

import common.interfaces.GameActionContext;
// import common.dto.JournalEntryDTO; // No longer needed here
import common.dto.TextMessage;
// No need for core imports if just using context

public class AskWatsonCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    public AskWatsonCommand() {
        super(true); // Requires case to be started
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // Get the hint or status message from the context
        String hintResult = context.askWatsonForHint(getPlayerId());

        // Check if the result indicates Watson wasn't there or unavailable
        if (hintResult.equals("Dr. Watson is not here to offer a hint.") ||
                hintResult.equals("Dr. Watson is unavailable in this case.") ||
                hintResult.equals("Your location is unknown.") ||
                hintResult.equals("Dr. Watson's location is unknown."))
        {
            // Just send the status message
            context.sendResponseToPlayer(getPlayerId(), new TextMessage(hintResult, false));
            return;
        }

        // If we got a real hint (or the default "no insight" message)
        // Format and send the response to the player
        String messageText = "Watson: \"" + hintResult + "\"";
        context.sendResponseToPlayer(getPlayerId(), new TextMessage(messageText, false));

    }

    @Override
    public String getDescription() {
        return "Asks Dr. Watson for a hint if he is in the same room.";
    }
}