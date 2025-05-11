package common.commands;

import common.interfaces.GameActionContext;
import common.dto.JournalEntryDTO;
import common.dto.TextMessage; // For "Journal is empty" or search results

import java.util.List;
import java.util.stream.Collectors;

public class JournalCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;
    private final String keyword; // Optional search keyword

    public JournalCommand() {
        this(null); // Default constructor for viewing all entries
    }

    public JournalCommand(String keyword) {
        super(true); // Requires case to be started
        this.keyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim().toLowerCase() : null;
    }

    public String getKeyword() {
        return keyword;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        List<JournalEntryDTO> entries = context.getJournalEntries(getPlayerId());

        if (entries.isEmpty()) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("Your journal is empty.", false));
            return;
        }

        List<JournalEntryDTO> filteredEntries = entries;
        String responseTitle = "Journal Contents:";

        if (this.keyword != null) {
            responseTitle = "Journal search results for '" + this.keyword + "':";
            filteredEntries = entries.stream()
                    .filter(entry -> entry.getText().toLowerCase().contains(this.keyword) ||
                            entry.getContributorPlayerId().toLowerCase().contains(this.keyword))
                    .collect(Collectors.toList());

            if (filteredEntries.isEmpty()) {
                context.sendResponseToPlayer(getPlayerId(), new TextMessage("No journal entries found matching '" + this.keyword + "'.", false));
                return;
            }
        }

        // For simplicity, sending each entry as a separate TextMessage.
        // A better approach for multiplayer would be to send a single DTO containing all relevant entries.
        // e.g., common.dto.JournalDisplayDTO(String title, List<JournalEntryDTO> entries)
        // For now, multiple messages:
        context.sendResponseToPlayer(getPlayerId(), new TextMessage(responseTitle, false));
        for (JournalEntryDTO entry : filteredEntries) {
            // DTO's toString() method should be well-formatted for display.
            context.sendResponseToPlayer(getPlayerId(), new TextMessage(entry.toString(), false));
        }
    }

    @Override
    public String getDescription() {
        return "Displays all journal entries. Usage: journal [optional_keyword_to_search]";
    }
}