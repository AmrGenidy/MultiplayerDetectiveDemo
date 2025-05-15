package common.commands;

import common.dto.JournalEntryDTO;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class JournalAddCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final String note;

  public JournalAddCommand(String note) {
    super(true); // Requires case to be started
    if (note == null || note.trim().isEmpty()) {
      throw new IllegalArgumentException("Note cannot be null or empty for JournalAddCommand.");
    }
    this.note = note.trim();
  }

  public String getNote() {
    return note;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    JournalEntryDTO newEntry =
        new JournalEntryDTO(
            this.note,
            getPlayerId(), // Contributor is the player issuing the command
            System.currentTimeMillis());
    context.addJournalEntry(newEntry); // This will add to the context's journal

    // The context.addJournalEntry method should ideally handle broadcasting
    // this new entry to all players in a multiplayer session if the journal is shared.
    // For now, send a confirmation to the player who added it.
    context.sendResponseToPlayer(
        getPlayerId(), new TextMessage("Note added to journal: \"" + this.note + "\"", false));
  }

  @Override
  public String getDescription() {
    return "Adds a custom note to your journal. Usage: journal add [your note text]";
  }
}
