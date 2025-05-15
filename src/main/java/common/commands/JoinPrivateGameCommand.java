package common.commands;

import common.dto.JoinPrivateGameRequestDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class JoinPrivateGameCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final JoinPrivateGameRequestDTO payload; // Store the request DTO

  public JoinPrivateGameCommand(JoinPrivateGameRequestDTO payload) {
    super(false); // Does not require case started
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null for JoinPrivateGameCommand.");
    }
    this.payload = payload;
  }

  public JoinPrivateGameRequestDTO getPayload() {
    return payload;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    // Server-side logic: GameSessionManager receives this, extracts the gameCode from payload,
    // finds the private lobby by code, attempts to add the player,
    // and sends back JoinGameResponseDTO.
    System.out.println(
        "Server received JoinPrivateGameCommand for code: "
            + payload.getGameCode()
            + " from player: "
            + getPlayerId());
    // Actual processing happens in GameSessionManager.processLobbyCommand
  }

  @Override
  public String getDescription() {
    return "Requests to join a specific private game session using a code.";
  }
}
