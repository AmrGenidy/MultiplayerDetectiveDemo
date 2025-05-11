package client.util;

import client.ClientState;
import common.commands.*;
import common.dto.HostGameRequestDTO;
import common.dto.JoinPrivateGameRequestDTO;
import common.dto.JoinPublicGameRequestDTO;
import common.dto.UpdateDisplayNameRequestDTO;

public class CommandFactoryClient {

    // Utility class.
    private CommandFactoryClient() {}

    /**
     * Creates a Command DTO based on parsed user input.
     * @param parsedData Parsed command name and arguments.
     * @param currentState Current state of the client (not actively used here but available).
     * @return A Command object or null if input is invalid/unknown.
     */
    public static Command createCommand(CommandParserClient.ParsedCommandData parsedData, ClientState currentState) {
        if (parsedData == null || parsedData.commandName == null || parsedData.commandName.isEmpty()) {
            return null;
        }

        String commandName = parsedData.commandName;
        String arg = parsedData.getFirstArgument();

        // --- Lobby & Session Setup Commands ---
        if ("host game".equals(commandName) || "host case".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: host game <case_title_or_number> [public/private]"); return null; }
            String caseTitle = arg;
            boolean isPublic = true;
            if (arg.toLowerCase().endsWith(" private")) {
                isPublic = false;
                caseTitle = arg.substring(0, arg.length() - " private".length()).trim();
            } else if (arg.toLowerCase().endsWith(" public")) {
                isPublic = true;
                caseTitle = arg.substring(0, arg.length() - " public".length()).trim();
            }
            return new HostGameCommand(new HostGameRequestDTO(caseTitle, isPublic));
        }
        if ("list public games".equals(commandName)) {
            return new ListPublicGamesCommand();
        }
        if ("join public game".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: join public game <session_id_or_number>"); return null; }
            return new JoinPublicGameCommand(new JoinPublicGameRequestDTO(arg));
        }
        if ("join private game".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: join private game <game_code>"); return null; }
            return new JoinPrivateGameCommand(new JoinPrivateGameRequestDTO(arg.toUpperCase()));
        }
        if ("request start case".equals(commandName)) {
            return new RequestStartCaseCommand();
        }
        if ("request final exam".equals(commandName)) {
            return new RequestInitiateExamCommand();
        }

        // --- In-Game Commands ---
        if ("start case".equals(commandName)) {
            return new StartCaseCommand();
        }
        if ("look".equals(commandName)) {
            return new LookCommand();
        }
        if ("move".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: move <direction>"); return null; }
            return new MoveCommand(arg);
        }
        if ("examine".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: examine <object_name>"); return null; }
            return new ExamineCommand(arg);
        }
        if ("question".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: question <suspect_name>"); return null; }
            return new QuestionCommand(arg);
        }
        if ("journal".equals(commandName)) {
            return new JournalCommand(arg); // arg can be null
        }
        if ("journal add".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: journal add <note_text>"); return null; }
            return new JournalAddCommand(arg);
        }
        if ("deduce".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: deduce <object_name>"); return null; }
            return new DeduceCommand(arg);
        }
        if ("ask watson".equals(commandName)) {
            return new AskWatsonCommand();
        }
        if ("tasks".equals(commandName)) {
            return new TaskCommand();
        }
        if ("initiate final exam".equals(commandName)) {
            return new InitiateFinalExamCommand();
        }
        if ("submit exam answer".equals(commandName)) {
            // Fallback if typed; primary submission is direct in GameClient.
            if (arg == null) { System.err.println("Usage: submit answer <q_num> <answer>"); return null; }
            String[] parts = arg.split("\\s+", 2);
            if (parts.length < 2) { System.err.println("Usage: submit answer <q_num> <answer>"); return null; }
            try {
                int qNum = Integer.parseInt(parts[0]);
                return new SubmitExamAnswerCommand(qNum, parts[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid question number for 'submit answer'.");
                return null;
            }
        }

        // --- General Client & Utility Commands ---
        if ("/setname".equals(commandName)) {
            if (arg == null || arg.isEmpty()) { System.err.println("Usage: /setname <new_display_name>"); return null; }
            return new UpdateDisplayNameCommand(new UpdateDisplayNameRequestDTO(arg));
        }
        if ("help".equals(commandName)) {
            return new HelpCommand();
        }
        if ("exit".equals(commandName)) {
            return new ExitCommand();
        }
        if ("add case".equals(commandName)) {
            System.err.println("'add case' is not a typical client-to-server command.");
            return null;
        }

        // Unrecognized command by this factory.
        return null;
    }
}