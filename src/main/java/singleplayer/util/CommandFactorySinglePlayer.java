package singleplayer.util;

import common.commands.*;


public class CommandFactorySinglePlayer {

    /**
     * Creates a Command object based on parsed user input for Single Player mode.
     * Assumes parsedInput[0] is the command name (lowercase) and parsedInput[1] (if exists) is the argument string.
     * Argument validation (e.g., move needs a direction) is handled here with error messages.
     * Handles mapping user input like "final exam" to the appropriate command object (InitiateFinalExamCommand).
     *
     * @param parsedInput String array from CommandParserSinglePlayer.parseInputSimple
     * @return A Command object ready to be executed, or null if the input is invalid or unknown.
     */
    public static Command createCommand(String[] parsedInput) {
        if (parsedInput == null || parsedInput.length == 0) {
            // This case should ideally be handled by the caller, but return null if it happens.
            return null;
        }

        String commandName = parsedInput[0].toLowerCase(); // Already lowercase from simple parser
        String arg = (parsedInput.length > 1) ? parsedInput[1] : null;

        switch (commandName) {
            case "look":
                return new LookCommand();
            case "move":
                if (arg != null && !arg.isEmpty()) return new MoveCommand(arg);
                else System.out.println("Move where? Please specify a direction (e.g., 'move north')."); return null;
            case "examine":
                if (arg != null && !arg.isEmpty()) return new ExamineCommand(arg);
                else System.out.println("Examine what? Please specify an object (e.g., 'examine dusty book')."); return null;
            case "question":
                if (arg != null && !arg.isEmpty()) return new QuestionCommand(arg);
                else System.out.println("Question whom? Please specify a suspect (e.g., 'question Lady Eleanor')."); return null;
            case "journal":
                // JournalCommand constructor accepts null arg for viewing all
                return new JournalCommand(arg);
            case "journal add":
                // JournalAddCommand constructor requires a non-null/non-empty note
                if (arg != null && !arg.isEmpty()) return new JournalAddCommand(arg);
                else System.out.println("Add what to journal? Please specify the note text (e.g., 'journal add suspicious stain')."); return null;
            case "deduce":
                if (arg != null && !arg.isEmpty()) return new DeduceCommand(arg);
                else System.out.println("Deduce from what? Please specify an object (e.g., 'deduce locked chest')."); return null;
            case "ask watson":
                return new AskWatsonCommand();
            case "final exam": // User types "final exam"
                return new InitiateFinalExamCommand(); // Creates the command to start the exam flow
            case "submit answer": // User types "submit answer 1 my response" - handled by game loop now
                System.out.println("Error: 'submit answer' should be handled directly by the game loop when answering questions, not as a standard command.");
                return null; // Factory shouldn't create this; SP main loop does
            case "tasks":
                return new TaskCommand();
            case "help":
                return new HelpCommand();
            case "start case":
                return new StartCaseCommand();
            case "exit":
                return new ExitCommand();
            case "add case": // Specific to SP file system interaction
                // Note: common.commands.AddCaseCommand might not exist or be suitable.
                // SP might need its own AddCaseSPCommand or static logic.
                // Assuming AddCaseCommand exists and takes filepath:
                if (arg != null && !arg.isEmpty()) {
                    // return new AddCaseCommand(arg); // If using common AddCaseCommand
                    System.out.println("Note: 'add case' functionality needs specific Single Player implementation.");
                    return null; // Placeholder - requires SP-specific file handling logic
                }
                else {
                    System.out.println("Add which case? Please specify a file path (e.g., 'add case C:\\cases\\my_case.json').");
                    return null;
                }

                // Default case for unrecognized commands
            default:
                // *** REMOVED PRINT STATEMENT FROM HERE ***
                // The SinglePlayerMain loop will print the "Unknown command" message if this factory returns null.
                return null;
        }
    }
}