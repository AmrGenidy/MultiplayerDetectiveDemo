package client.util;

public class CommandParserClient {

    // Utility class, no instances.
    private CommandParserClient() {}

    /**
     * Parses raw user input into a command name and arguments.
     * @param rawInput The user's typed string.
     * @return ParsedCommandData object or null if input is empty.
     */
    public static ParsedCommandData parse(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return null;
        }

        String normalizedInput = rawInput.trim().toLowerCase(); // Normalize for matching

        // Check for multi-word or specific prefix commands first for accuracy.
        if (normalizedInput.startsWith("host game") || normalizedInput.startsWith("host case")) {
            return new ParsedCommandData("host game", extractArgs(normalizedInput, "host game"));
        } else if (normalizedInput.startsWith("list games") || normalizedInput.startsWith("list public games")) {
            return new ParsedCommandData("list public games", extractArgs(normalizedInput, "list public games"));
        } else if (normalizedInput.startsWith("join public game")) {
            return new ParsedCommandData("join public game", extractArgs(normalizedInput, "join public game"));
        } else if (normalizedInput.startsWith("join private game") || normalizedInput.startsWith("join game")) {
            return new ParsedCommandData("join private game", extractArgs(normalizedInput, "join private game"));
        } else if (normalizedInput.equals("request start case")) { // No args
            return new ParsedCommandData("request start case", new String[0]);
        } else if (normalizedInput.equals("request final exam")) { // No args
            return new ParsedCommandData("request final exam", new String[0]);
        } else if (normalizedInput.startsWith("start case")) {
            return new ParsedCommandData("start case", extractArgs(normalizedInput, "start case"));
        } else if (normalizedInput.startsWith("initiate final exam") || normalizedInput.startsWith("final exam")) {
            return new ParsedCommandData("initiate final exam", extractArgs(normalizedInput, "initiate final exam"));
        } else if (normalizedInput.startsWith("submit answer") || normalizedInput.startsWith("submit exam answer")) {
            return new ParsedCommandData("submit exam answer", extractArgs(normalizedInput, "submit exam answer"));
        } else if (normalizedInput.startsWith("journal add")) {
            return new ParsedCommandData("journal add", extractArgs(normalizedInput, "journal add"));
        } else if (normalizedInput.startsWith("ask watson")) {
            return new ParsedCommandData("ask watson", extractArgs(normalizedInput, "ask watson"));
        } else if (normalizedInput.startsWith("add case")) {
            return new ParsedCommandData("add case", extractArgs(normalizedInput, "add case"));
        } else if (normalizedInput.startsWith("/setname ")) {
            return new ParsedCommandData("/setname", extractArgs(normalizedInput, "/setname"));
        }

        // Fallback: first word is command, rest is argument.
        String[] tokens = normalizedInput.split("\\s+", 2);
        String commandName = tokens[0];
        String[] args = (tokens.length > 1 && tokens[1] != null && !tokens[1].isEmpty())
                ? new String[]{tokens[1].trim()}
                : new String[0];

        return new ParsedCommandData(commandName, args);
    }

    /**
     * Extracts arguments after a given command prefix.
     * Returns a single string argument or an empty array.
     */
    private static String[] extractArgs(String fullInput, String commandPrefix) {
        // Ensure prefix ends with a space for correct substring, unless it's an exact match command
        String effectivePrefix = commandPrefix.endsWith(" ") ? commandPrefix : commandPrefix + " ";

        if (fullInput.startsWith(effectivePrefix) && fullInput.length() > effectivePrefix.length()) {
            String argPart = fullInput.substring(effectivePrefix.length()).trim();
            if (!argPart.isEmpty()) {
                return new String[]{argPart};
            }
        } else if (fullInput.equals(commandPrefix.trim())) { // For commands like "start case" that might not have args
            return new String[0]; // Exact match, no arguments after it
        }
        return new String[0]; // No arguments found
    }

    /**
     * Container for parsed command name and its arguments.
     */
    public static class ParsedCommandData {
        public final String commandName;
        public final String[] arguments; // Typically one element: the rest of the input line.

        public ParsedCommandData(String commandName, String[] arguments) {
            this.commandName = commandName;
            this.arguments = arguments;
        }

        /**
         * Gets the first (often only) argument.
         * @return Argument string or null if no arguments.
         */
        public String getFirstArgument() {
            return (this.arguments != null && this.arguments.length > 0) ? this.arguments[0] : null;
        }
    }
}