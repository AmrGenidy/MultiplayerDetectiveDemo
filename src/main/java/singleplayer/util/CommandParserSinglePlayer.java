package singleplayer.util;

// This parser focuses on identifying the command name and basic tokenization.
// Argument validation (e.g., ensuring 'move' has a direction) is often
// handled by the command factory or the command constructor itself.
public class CommandParserSinglePlayer {

    /**
     * Parses the user input to extract the command name and arguments.
     * Handles multi-word command names by checking for them specifically.
     *
     * @param input The raw user input string.
     * @return A String array where the first element is the command name (normalized to lowercase)
     *         and subsequent elements are arguments. Returns an empty array if input is null/empty.
     */
    public static String[] parseInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new String[0];
        }

        String normalizedInput = input.trim().replaceAll("\\s+", " ").toLowerCase();
        String[] tokens = normalizedInput.split(" ", -1); // Split by space, keep trailing empty strings if any

        if (tokens.length == 0) {
            return new String[0];
        }

        // Check for multi-word commands first
        if (normalizedInput.startsWith("journal add ")) {
            // "journal add" is the command, the rest is the argument
            if (tokens.length >= 3) {
                String note = normalizedInput.substring("journal add ".length());
                return new String[]{"journal add", note};
            } else {
                return new String[]{"journal add"}; // Missing argument
            }
        } else if (normalizedInput.startsWith("start case")) { // No arguments expected
            return new String[]{"start case"};
        } else if (normalizedInput.startsWith("ask watson")) { // No arguments
            return new String[]{"ask watson"};
        } else if (normalizedInput.startsWith("final exam")) { // Changed to InitiateFinalExamCommand, no args client-side
            return new String[]{"final exam"}; // This will map to InitiateFinalExamCommand
        }
        // For other commands, the first token is the command name
        // and the rest are arguments.
        String commandName = tokens[0];
        if (tokens.length > 1) {
            String[] args = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, args, 0, tokens.length - 1);
            // Reconstruct arguments if they were split but belong together (e.g. examine old book)
            // For commands like 'examine', 'question', 'deduce', 'move', 'add case'
            // the factory will expect the argument as a single string if it's multi-word.
            if (isCommandWithPotentialMultiWordArg(commandName) && args.length > 0) {
                String multiWordArg = normalizedInput.substring(commandName.length()).trim();
                return new String[]{commandName, multiWordArg};
            }
            return new String[]{commandName, String.join(" ", args)}; // Fallback if not specifically handled
        } else {
            return new String[]{commandName}; // Command with no arguments
        }
    }

    private static boolean isCommandWithPotentialMultiWordArg(String commandName) {
        switch(commandName) {
            case "examine":
            case "question":
            case "deduce":
            case "move": // Though move usually takes one word direction
            case "add case": // File path can have spaces
            case "journal": // Keyword for journal search
                return true;
            default:
                return false;
        }
    }

    // Simpler version, if you want to handle multi-word args more generally in factory
    public static String[] parseInputSimple(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new String[0];
        }
        // Normalize: lowercase, trim, collapse multiple spaces
        String normalizedInput = input.trim().replaceAll("\\s+", " ").toLowerCase();

        // Handle specific multi-word commands first
        if (normalizedInput.startsWith("journal add ")) {
            return new String[]{"journal add", normalizedInput.substring("journal add ".length()).trim()};
        }
        if (normalizedInput.equals("start case")) return new String[]{"start case"};
        if (normalizedInput.equals("ask watson")) return new String[]{"ask watson"};
        if (normalizedInput.equals("final exam")) return new String[]{"final exam"}; // Will map to InitiateFinalExamCommand

        // For single-word commands or commands where the first word is the command
        // and the rest is a single argument string.
        String[] parts = normalizedInput.split(" ", 2);
        return parts;
    }
}