package common.commands;

import Core.Room;
import Core.Suspect;
import common.dto.JournalEntryDTO;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.util.List;

public class QuestionCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final String suspectName;

  public QuestionCommand(String suspectName) {
    super(true); // Requires case to be started
    if (suspectName == null || suspectName.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "Suspect name cannot be null or empty for QuestionCommand.");
    }
    this.suspectName = suspectName.trim();
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());
    if (currentRoom == null) {
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage("Error: You are not in a valid room to question anyone.", true));
      return;
    }

    Suspect targetSuspect = null;
    // Find the suspect in the current room by name
    List<Suspect> suspectsInRoom =
        context.getAllSuspects().stream()
            .filter(
                s ->
                    s.getCurrentRoom() != null
                        && s.getCurrentRoom().getName().equalsIgnoreCase(currentRoom.getName()))
            .filter(s -> s.getName().equalsIgnoreCase(this.suspectName))
            .collect(java.util.stream.Collectors.toList());

    if (!suspectsInRoom.isEmpty()) {
      targetSuspect = suspectsInRoom.get(0); // Should ideally be unique by name in a room
    }

    if (targetSuspect == null) {
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage("Suspect '" + this.suspectName + "' is not in this room.", false));
      return;
    }

    String statement = targetSuspect.getStatement(); // Assuming Suspect has getStatement()
    if (statement == null || statement.trim().isEmpty()) {
      statement =
          targetSuspect.getName() + " has nothing to say or seems unwilling to talk right now.";
    }

    String messageText = targetSuspect.getName() + " says: \"" + statement + "\"";
    context.sendResponseToPlayer(getPlayerId(), new TextMessage(messageText, false));

    // Add to journal
    String journalText = "Questioned " + targetSuspect.getName() + ": " + statement;
    context.addJournalEntry(
        new JournalEntryDTO(journalText, getPlayerId(), System.currentTimeMillis()));
  }

  @Override
  public String getDescription() {
    return "Questions a suspect in the current room. Usage: question [suspect_name]";
  }
}
