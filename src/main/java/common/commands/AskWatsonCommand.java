package common.commands;

import common.dto.TextMessage;
import common.dto.WatsonHintResponseDTO; // Import the new DTO
import common.interfaces.GameActionContext;
import java.io.Serial;

public class AskWatsonCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;

  public AskWatsonCommand() {
    super(true); // Requires case to be started
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    WatsonHintResponseDTO watsonResponse = context.askWatsonForHint(getPlayerId());

    if (watsonResponse == null) { // Defensive check, context should always return an object
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("Error receiving response from Watson.", true));
      return;
    }

    String messageContent = watsonResponse.getMessage();

    if (watsonResponse.isActualHint()) {
      String formattedHintMessage = "Watson: \"" + messageContent + "\"";
      context.sendResponseToPlayer(getPlayerId(), new TextMessage(formattedHintMessage, false));
    } else {
      // It's a status message (e.g., "Dr. Watson is not here...", "Watson has no insight...")
      // Send this message directly without the "Watson: " prefix or extra quotes,
      // as the message from WatsonHintResponseDTO should already be user-friendly.
      context.sendResponseToPlayer(getPlayerId(), new TextMessage(messageContent, false));
    }
  }

  @Override
  public String getDescription() {
    return "Asks Dr. Watson for a hint if he is in the same room.";
  }
}