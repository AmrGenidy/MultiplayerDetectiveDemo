package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage; // For server response

public class RequestStartCaseCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    public RequestStartCaseCommand() {
        super(false); // Can be issued before case is formally started by host
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // Server-side: GameContextServer will receive this.
        // It should check if the sender is the guest.
        // If so, it prompts the host.
        // If sender is host, or conditions not met, it sends an appropriate TextMessage.
        context.processRequestStartCase(getPlayerId()); // New method in GameActionContext
    }

    @Override
    public String getDescription() {
        return "Requests the host to start the case investigation.";
    }
}