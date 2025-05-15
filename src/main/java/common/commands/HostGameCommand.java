package common.commands;

import common.dto.HostGameRequestDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

// common.dto.HostGameResponseDTO will be sent by the context

public class HostGameCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final HostGameRequestDTO payload;

  public HostGameCommand(HostGameRequestDTO payload) {
    super(false); // Does not require case to be started
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null for HostGameCommand.");
    }
    this.payload = payload;
  }

  public HostGameRequestDTO getPayload() {
    return payload;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    // The server-side GameActionContext (or GameSessionManager) handles this:
    // 1. Validates payload.getCaseTitle().
    // 2. Creates a new game session.
    // 3. Generates a gameCode if private.
    // 4. Sends a HostGameResponseDTO (with success/failure, message, sessionId, gameCode)
    //    back to the player.
    // This command DTO just carries the request.
    // Conceptual call: serverLogic.processHostGameRequest(getPlayerId(), payload);
    System.out.println(
        "Server received HostGameCommand from player: "
            + getPlayerId()
            + " for case: "
            + payload.getCaseTitle());
  }

  @Override
  public String getDescription() {
    return "Requests to host a new game with a selected case. Usage: host game [case_title] [public/private]";
  }
}
