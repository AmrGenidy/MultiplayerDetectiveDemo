package common.commands;

import Core.GameObject;
import Core.Room;
import common.dto.JournalEntryDTO;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class ExamineCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final String objectName;

  public ExamineCommand(String objectName) {
    super(true); // Requires case to be started
    if (objectName == null || objectName.trim().isEmpty()) {
      throw new IllegalArgumentException("Object name cannot be null or empty for ExamineCommand.");
    }
    this.objectName = objectName.trim();
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());
    if (currentRoom == null) {
      context.sendResponseToPlayer(
          getPlayerId(), new TextMessage("Error: You are not in a valid room.", true));
      return;
    }

    GameObject objectToExamine = currentRoom.getObject(this.objectName);

    if (objectToExamine == null) {
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage("There is no '" + this.objectName + "' to examine here.", false));
      return;
    }

    String examinationResult = objectToExamine.getExamine(); // Assuming GameObject has getExamine()
    if (examinationResult == null || examinationResult.trim().isEmpty()) {
      examinationResult = objectToExamine.getDescription(); // Fallback to description
    }

    String messageText = "You examine the " + objectToExamine.getName() + ": " + examinationResult;
    context.sendResponseToPlayer(getPlayerId(), new TextMessage(messageText, false));

    // Add to journal
    String journalText = "Examined " + objectToExamine.getName() + ": " + examinationResult;
    context.addJournalEntry(
        new JournalEntryDTO(journalText, getPlayerId(), System.currentTimeMillis()));
    // The context.addJournalEntry might also broadcast this journal update to other players in MP.
  }

  @Override
  public String getDescription() {
    return "Inspects an object for clues. Usage: examine [object_name]";
  }
}
