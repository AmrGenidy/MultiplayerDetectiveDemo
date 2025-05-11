package common.commands;
// No GameActionContext needed for simple DTO command

public class RequestCaseListCommand extends BaseCommand { // BaseCommand to carry playerId
    private static final long serialVersionUID = 1L;
    private final boolean forPublicHosting; // true if for public, false if for private

    public RequestCaseListCommand(boolean forPublicHosting) {
        super(false); // Does not require case to be started
        this.forPublicHosting = forPublicHosting;
    }

    public boolean isForPublicHosting() {
        return forPublicHosting;
    }

    @Override
    protected void executeCommandLogic(common.interfaces.GameActionContext context) {
        // Server-side: GameSessionManager gets this. It knows the intent.
        // It prepares AvailableCasesDTO and sends it back.
        // No client-side execution logic needed for DTO commands.
    }

    @Override
    public String getDescription() {
        return "Client request for available case list (internal).";
    }
}