package common.commands;

import common.dto.TextMessage;
import common.interfaces.GameActionContext;

public abstract class BaseCommand implements Command {
  // serialVersionUID from Command interface is inherited if Command is an interface.
  // If Command was an abstract class, BaseCommand would need its own.
  // For interfaces, it's more about the implementing class having it.

  protected String playerId;
  protected boolean requiresCaseStarted;

  public BaseCommand(boolean requiresCaseStarted) {
    this.requiresCaseStarted = requiresCaseStarted;
  }

  @Override
  public String getPlayerId() {
    return playerId;
  }

  @Override
  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  @Override
  public final void execute(GameActionContext context) {
    if (playerId == null || playerId.trim().isEmpty()) {
      // This check should ideally happen on the server before execution
      // or an exception should be thrown if playerId is not set.
      System.err.println("Error: Player ID not set for command: " + getClass().getSimpleName());
      // context.sendResponseToPlayer(playerId, new TextMessage("Internal error: Player ID
      // missing.", true));
      // Cannot send response if playerId is null. This indicates a server-side logic error.
      return;
    }
    if (requiresCaseStarted && !context.isCaseStarted()) {
      context.sendResponseToPlayer(
          playerId,
          new TextMessage("The case has not started yet. Use 'start case' to begin.", true));
      return;
    }
    executeCommandLogic(context);
  }

  /**
   * Subclasses must implement this method to define their specific command logic.
   *
   * @param context The GameActionContext to operate on.
   */
  protected abstract void executeCommandLogic(GameActionContext context);
}
