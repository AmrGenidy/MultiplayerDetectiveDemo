package common.commands;

import common.dto.JoinPublicGameRequestDTO; // Import the request DTO
import common.interfaces.GameActionContext;

public class JoinPublicGameCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;
    private final JoinPublicGameRequestDTO payload; // Store the request DTO

    public JoinPublicGameCommand(JoinPublicGameRequestDTO payload) {
        super(false); // Does not require case started
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null for JoinPublicGameCommand.");
        }
        this.payload = payload;
    }

    public JoinPublicGameRequestDTO getPayload() {
        return payload;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // Server-side logic: GameSessionManager receives this command, extracts the sessionId from payload,
        // finds the public lobby, attempts to add the player (identified by getPlayerId()),
        // and sends back a JoinGameResponseDTO.
        // This command object itself doesn't do much beyond carrying the payload.
        System.out.println("Server received JoinPublicGameCommand for session: " + payload.getSessionId() + " from player: " + getPlayerId());
        // Actual processing happens in GameSessionManager.processLobbyCommand
    }

    @Override
    public String getDescription() {
        return "Requests to join a specific public game session.";
    }
}