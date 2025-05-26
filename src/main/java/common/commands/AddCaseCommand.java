package common.commands;

import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

/**
 * AddCaseCommand (Potentially for Admin Use) Represents an intent to add a new case from a file
 * path. In a client-server setup, this is typically an admin-level operation, and the server must
 * handle file paths securely.
 */
@SuppressWarnings("unused") // Kept for potential future admin client or server-side direct use
public class AddCaseCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final String filePath;

  public AddCaseCommand(String filePath) {
    super(false);
    if (filePath == null || filePath.trim().isEmpty()) {
      throw new IllegalArgumentException("File path cannot be null or empty for AddCaseCommand.");
    }
    this.filePath = filePath.trim();
  }

  public String getFilePath() {
    return filePath;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    // This command, if received by a server from a client, needs robust security.
    // The server context should decide if this player/ID is authorized and
    // how to interpret/use the filePath safely (e.g., from a predefined server directory).
    // context.processAdminAddCase(getPlayerId(), filePath); // Hypothetical context method

    // Generic acknowledgement if it reaches here
    context.sendResponseToPlayer(
        getPlayerId(),
        new TextMessage(
            "Add case request for '"
                + filePath
                + "' received by context. Processing depends on permissions/mode.",
            false));
  }

  @Override
  public String getDescription() {
    return "ADMIN: Adds a new case from a server-accessible JSON file. Usage: add case [server_side_path_or_identifier]";
  }
}
