package common.commands;

import common.dto.UpdateDisplayNameRequestDTO;
import common.interfaces.GameActionContext;

public class UpdateDisplayNameCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;
    private final UpdateDisplayNameRequestDTO payload;

    public UpdateDisplayNameCommand(UpdateDisplayNameRequestDTO payload) {
        super(false); // Can be used whether case is started or not
        this.payload = payload;
    }

    public UpdateDisplayNameRequestDTO getPayload() {
        return payload;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // Server-side: GameContextServer or GameSessionManager will handle this
        // It needs to access the ClientSession object associated with getPlayerId()
        // and update its display name, then broadcast PlayerNameChangedDTO.
        context.processUpdateDisplayName(getPlayerId(), payload.getNewDisplayName());
    }

    @Override
    public String getDescription() {
        return "Requests the server to update the player's display name.";
    }
}