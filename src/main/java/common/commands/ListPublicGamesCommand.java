package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serial;

// common.dto.PublicGamesListDTO will be sent by the context

public class ListPublicGamesCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;

  public ListPublicGamesCommand() {
    super(false); // Does not require case to be started
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    // Server-side GameActionContext (or GameSessionManager) handles:
    // 1. Retrieving the list of current public game lobbies (as PublicGameInfoDTOs).
    // 2. Sending a PublicGamesListDTO back to the player.
    // Conceptual call: serverLogic.sendPublicGamesListToPlayer(getPlayerId());
    System.out.println("Server received ListPublicGamesCommand from player: " + getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Requests a list of currently available public games to join.";
  }
}
