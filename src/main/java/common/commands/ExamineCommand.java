package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage;
import common.dto.JournalEntryDTO; // For adding to journal
import Core.GameObject; // For type reference if needed
import Core.Room; // For type reference if needed

public class ExamineCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;
    private final String objectName;

    public ExamineCommand(String objectName) {
        super(true); // Requires case to be started
        if (objectName == null || objectName.trim().isEmpty()) {
            throw new IllegalArgumentException("Object name cannot be null or empty for ExamineCommand.");
        }
        this.objectName = objectName.trim();
    }

    public String getObjectName() {
        return objectName;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());
        if (currentRoom == null) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("Error: You are not in a valid room.", true));
            return;
        }

        GameObject objectToExamine = currentRoom.getObject(this.objectName);

        if (objectToExamine == null) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("There is no '" + this.objectName + "' to examine here.", false));
            return;
        }

        String examinationResult = objectToExamine.getExamine(); // Assuming GameObject has getExamine()
        if (examinationResult == null || examinationResult.trim().isEmpty()) {
            examinationResult = objectToExamine.getDescription(); // Fallback to description
        }

        String messageText = "You examine the " + objectToExamine.getName() + ": " + examinationResult;
        context.sendResponseToPlayer(getPlayerId(), new TextMessage(messageText, false));

        // Add to journal
        String journalText = "Examined " + objectToExamine.getName() + ": " + examinationResult;
        context.addJournalEntry(new JournalEntryDTO(journalText, getPlayerId(), System.currentTimeMillis()));
        // The context.addJournalEntry might also broadcast this journal update to other players in MP.
    }

    @Override
    public String getDescription() {
        return "Inspects an object for clues. Usage: examine [object_name]";
    }
}