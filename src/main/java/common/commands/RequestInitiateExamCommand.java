package common.commands;

import common.interfaces.GameActionContext;


public class RequestInitiateExamCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    public RequestInitiateExamCommand() {
        super(true); // Case must be started to request exam
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // Server-side: GameContextServer will receive this.
        // It should check if the sender is the guest.
        // If so, it prompts the host.
        // If sender is host, or conditions not met, it sends an appropriate TextMessage.
        context.processRequestInitiateExam(getPlayerId()); // New method in GameActionContext
    }

    @Override
    public String getDescription() {
        return "Requests the host to initiate the final exam.";
    }
}