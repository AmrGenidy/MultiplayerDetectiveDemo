package client.util;

// import client.ClientState; // NO LONGER NEEDED if parameter is removed
import common.commands.*;
import common.dto.HostGameRequestDTO;
import common.dto.JoinPrivateGameRequestDTO;
import common.dto.JoinPublicGameRequestDTO;
import common.dto.UpdateDisplayNameRequestDTO;

public class CommandFactoryClient {

  private CommandFactoryClient() {}

  /**
   * Creates a Command DTO based on parsed user input.
   *
   * @param parsedData Parsed command name and arguments.
   * @return A Command object or null if input is invalid/unknown.
   */
  public static Command createCommand(CommandParserClient.ParsedCommandData parsedData) {
    if (parsedData == null || parsedData.commandName == null || parsedData.commandName.isEmpty()) {
      return null;
    }

    String commandName = parsedData.commandName;
    String arg = parsedData.getFirstArgument();

    // --- Lobby & Session Setup Commands ---
    switch (commandName) {
      case "host game", "host case" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: host game <case_title_or_number> [public/private]");
          return null;
        } // Keep usage hint
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

      // ... (rest of the method remains the same, just without using 'currentState') ...
      case "list public games" -> {
        return new ListPublicGamesCommand();
      }
      case "join public game" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: join public game <session_id_or_number>");
          return null;
        }
        return new JoinPublicGameCommand(new JoinPublicGameRequestDTO(arg));
      }
      case "join private game" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: join private game <game_code>");
          return null;
        }
        return new JoinPrivateGameCommand(new JoinPrivateGameRequestDTO(arg.toUpperCase()));
      }
      case "request start case" -> {
        return new RequestStartCaseCommand();
      }
      case "request final exam" -> {
        return new RequestInitiateExamCommand();
      }

      // --- In-Game Commands ---
      case "start case" -> {
        return new StartCaseCommand();
      }
      case "look" -> {
        return new LookCommand();
      }
      case "move" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: move <direction>");
          return null;
        }
        return new MoveCommand(arg);
      }
      case "examine" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: examine <object_name>");
          return null;
        }
        return new ExamineCommand(arg);
      }
      case "question" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: question <suspect_name>");
          return null;
        }
        return new QuestionCommand(arg);
      }
      case "journal" -> {
        return new JournalCommand(arg);
      }
      case "journal add" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: journal add <note_text>");
          return null;
        }
        return new JournalAddCommand(arg);
      }
      case "deduce" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: deduce <object_name>");
          return null;
        }
        return new DeduceCommand(arg);
      }
      case "ask watson" -> {
        return new AskWatsonCommand();
      }
      case "tasks" -> {
        return new TaskCommand();
      }
      case "initiate final exam" -> {
        return new InitiateFinalExamCommand();
      }
      case "submit exam answer" -> {
        if (arg == null) {
          System.err.println("Usage: submit answer <q_num> <answer>");
          return null;
        }
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
          System.err.println("Usage: submit answer <q_num> <answer>");
          return null;
        }
        try {
          int qNum = Integer.parseInt(parts[0]);
          return new SubmitExamAnswerCommand(qNum, parts[1]);
        } catch (NumberFormatException e) {
          System.err.println("Invalid question number for 'submit answer'.");
          return null;
        }
      }

      // --- General Client & Utility Commands ---
      case "/setname" -> {
        if (arg == null || arg.isEmpty()) {
          System.err.println("Usage: /setname <new_display_name>");
          return null;
        }
        return new UpdateDisplayNameCommand(new UpdateDisplayNameRequestDTO(arg));
      }
      case "help" -> {
        return new HelpCommand();
      }
      case "exit" -> {
        return new ExitCommand();
      }
      case "add case" -> {
        System.err.println("'add case' is not a typical client-to-server command."); // Kept hint

        return null;
      }
    }

    return null; // Unrecognized command
  }
}
