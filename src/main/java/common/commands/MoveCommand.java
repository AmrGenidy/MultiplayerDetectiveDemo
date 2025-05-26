package common.commands;

import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class MoveCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final String direction;

  public MoveCommand(String direction) {
    super(true); // Requires case to be started
    if (direction == null || direction.trim().isEmpty()) {
      throw new IllegalArgumentException("Direction cannot be null or empty for MoveCommand.");
    }
    this.direction = direction.trim().toLowerCase();
  }

  public String getDirection() {
    return direction;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    boolean success = context.movePlayer(getPlayerId(), direction);
    // The GameActionContext.movePlayer implementation is now responsible for sending
    // the new RoomDescriptionDTO to the player if successful,
    // and potentially a TextMessage if not successful.
    // This command DTO itself doesn't need to send the response, just trigger the action.
    if (!success) {
      // Optionally, the context's movePlayer could return a more detailed status or handle the
      // error message.
      // For now, let's assume context.movePlayer sends its own failure message if needed.
      // Or, if movePlayer only returns boolean:
      context.sendResponseToPlayer(
          getPlayerId(), new TextMessage("You can't move " + direction + ".", false));
    }
    // If successful, context.movePlayer should have sent the new RoomDescriptionDTO.
    // It also needs to call context.notifyPlayerMove() and context.updateNpcMovements().
  }

  @Override
  public String getDescription() {
    return "Moves your character to a neighboring room. Usage: move [direction]";
  }
}
