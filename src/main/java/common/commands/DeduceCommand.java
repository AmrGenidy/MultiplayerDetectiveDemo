package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage;
import common.dto.JournalEntryDTO;
import Core.GameObject;
import Core.Room;
import Core.Detective;

public class DeduceCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;
    private final String objectName;

    public DeduceCommand(String objectName) {
        super(true); // Requires case to be started
        if (objectName == null || objectName.trim().isEmpty()) {
            throw new IllegalArgumentException("Object name cannot be null or empty for DeduceCommand.");
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
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("Error: You are not in a valid room to make deductions.", true));
            return;
        }

        GameObject objectToDeduce = currentRoom.getObject(this.objectName);
        if (objectToDeduce == null) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("There is no '" + this.objectName + "' here to deduce from.", false));
            return;
        }

        // Multiplayer: Check if this object was already deduced in this session
        // This logic needs to be in GameActionContext.deduceFromObjectInPlayerRoom
        // String deductionResult = context.deduceFromObjectInPlayerRoom(getPlayerId(), this.objectName);
        // The context method would handle shared state, incrementing shared deduce count, etc.
        // For now, let's simulate the core logic here and assume context will be refactored.

        Detective playerDetective = context.getPlayerDetective(getPlayerId());
        if (playerDetective.hasDeducedObject(objectToDeduce.getName().toLowerCase())) { // Check detective's personal history
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("You have already made a deduction about the " + objectToDeduce.getName() + ". Check your journal.", false));
            return;
        }
        // In MP, you'd also check a session-wide list of deduced objects.

        String clue = objectToDeduce.getDeduce(); // Assuming GameObject has getDeduce()
        if (clue == null || clue.trim().isEmpty()) {
            clue = "You ponder about the " + objectToDeduce.getName() + " but gain no new insights.";
            context.sendResponseToPlayer(getPlayerId(), new TextMessage(clue, false));
            return;
        }

        String messageText = "Deduction from " + objectToDeduce.getName() + ": " + clue;
        context.sendResponseToPlayer(getPlayerId(), new TextMessage(messageText, false));

        // Update detective state and add to journal
        boolean newDeduction = playerDetective.incrementDeduceCount(objectToDeduce.getName().toLowerCase());
        // In MP, the context would manage a shared deduce count if applicable,
        // or individual counts which contribute to a team total.
        // For now, we assume individual tracking on Detective object for rank.

        String journalText = "Deduced from " + objectToDeduce.getName() + ": " + clue;
        context.addJournalEntry(new JournalEntryDTO(journalText, getPlayerId(), System.currentTimeMillis()));

        // Notify about deduce count if it's a game mechanic affecting rank
        context.sendResponseToPlayer(getPlayerId(), new TextMessage("Your deductions used: " + playerDetective.getDeduceCount(), false));

    }

    @Override
    public String getDescription() {
        return "Makes a deduction about an object, revealing a clue. Affects rank. Usage: deduce [object_name]";
    }
}