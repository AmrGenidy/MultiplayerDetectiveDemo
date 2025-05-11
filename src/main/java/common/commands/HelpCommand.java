package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage;

import java.util.Map;
import java.util.LinkedHashMap; // To maintain order if commands are fetched from a factory

// This command's output depends on how available commands are determined.
// If commands are registered in a CommandFactory (client or server side), it can query that.
// For now, it might send a predefined list or rely on the context to provide command info.

public class HelpCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    public HelpCommand() {
        // Help can be accessed before or after case start, so `requiresCaseStarted` could be false
        // if we want a general help. Or true if help is context-specific to in-game commands.
        // Let's make it accessible anytime, but context might filter commands shown.
        super(false);
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {

        Map<String, String> commandsToShow = new LinkedHashMap<>();
        // This map would ideally be populated dynamically by the context or a server-side command registry.
        if (context.isCaseStarted()) {
            commandsToShow.put("look", "View surroundings.");
            commandsToShow.put("move [direction]", "Move to another room.");
            commandsToShow.put("examine [object]", "Inspect an object.");
            commandsToShow.put("question [suspect]", "Question a suspect.");
            commandsToShow.put("deduce [object]", "Make a deduction about an object.");
            commandsToShow.put("journal", "View your journal.");
            commandsToShow.put("journal add [note]", "Add a note to your journal.");
            commandsToShow.put("tasks", "View case tasks.");
            commandsToShow.put("ask watson", "Ask Dr. Watson for a hint.");
            commandsToShow.put("final exam", "Initiate the final exam (if conditions met)."); // Changed from "final exam" command
            commandsToShow.put("exit", "Exit the current case (MP) or game (SP).");
        } else {
            // Pre-case commands (mostly for multiplayer client)
            commandsToShow.put("host case [case_name]", "Host a new game (MP).");
            commandsToShow.put("list games", "List public games (MP).");
            commandsToShow.put("join game [id_or_code]", "Join a game (MP).");
            commandsToShow.put("start case", "Start the selected case investigation.");
            commandsToShow.put("add case [filepath]", "Add a new case file (SP or Server Admin).");
            commandsToShow.put("exit", "Exit the application.");
        }
        commandsToShow.put("help", "Display this help message.");


        StringBuilder helpMessage = new StringBuilder("Available commands:\n");
        for (Map.Entry<String, String> entry : commandsToShow.entrySet()) {
            helpMessage.append(String.format("  %-28s - %s\n", entry.getKey(), entry.getValue()));
        }

        context.sendResponseToPlayer(getPlayerId(), new TextMessage(helpMessage.toString().trim(), false));
    }

    @Override
    public String getDescription() {
        return "Displays a list of available commands and their descriptions.";
    }
}