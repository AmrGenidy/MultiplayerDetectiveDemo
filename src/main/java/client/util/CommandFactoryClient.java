package client.util;

import client.ClientState;
import common.commands.*;
import common.dto.HostGameRequestDTO;
import common.dto.JoinPrivateGameRequestDTO;
import common.dto.JoinPublicGameRequestDTO;
import common.dto.UpdateDisplayNameRequestDTO;

public class CommandFactoryClient {

  private CommandFactoryClient() {}

  /**
   * Creates a Command DTO based on parsed user input and the client's role.
   * @param parsedData Parsed command name and arguments.
   * @param isHost True if the current client is the host of the session, false otherwise.
   * @param currentClientState The current state of the client.
   * @return A Command object or null if input is invalid/unknown.
   */
  public static Command createCommand(CommandParserClient.ParsedCommandData parsedData, boolean isHost, ClientState currentClientState) {
    if (parsedData == null || parsedData.commandName == null || parsedData.commandName.isEmpty()) {
      return null;
    }

    String commandName = parsedData.commandName;
    String arg = parsedData.getFirstArgument();

    switch (commandName) {
      // --- Lobby & Session Setup Commands ---
      case "host game":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: host game <case_title_or_number> [public/private]"); return null; }
        String caseTitle = arg;
        boolean isPublic = true;
        if (arg.toLowerCase().endsWith(" private")) { isPublic = false; caseTitle = arg.substring(0, arg.length() - " private".length()).trim(); }
        else if (arg.toLowerCase().endsWith(" public")) { caseTitle = arg.substring(0, arg.length() - " public".length()).trim(); }
        return new HostGameCommand(new HostGameRequestDTO(caseTitle, isPublic));
      case "list public games":
        return new ListPublicGamesCommand();
      case "join public game":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: join public game <session_id_or_number>"); return null; }
        return new JoinPublicGameCommand(new JoinPublicGameRequestDTO(arg));
      case "join private game":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: join private game <game_code>"); return null; }
        return new JoinPrivateGameCommand(new JoinPrivateGameRequestDTO(arg.toUpperCase()));

      // --- Role-Dependent Commands ---
      case "start case":
        if (currentClientState == ClientState.IN_LOBBY_AWAITING_START) {
          return isHost ? new StartCaseCommand() : new RequestStartCaseCommand();
        } else {
          System.err.println("CLIENT_FACTORY_HINT: 'start case' can only be used when the lobby is full and awaiting the game to start.");
          return null;
        }


      case "initiate final exam":
        if (currentClientState == ClientState.IN_GAME) {
          return isHost ? new InitiateFinalExamCommand() : new RequestInitiateExamCommand();
        } else {
          System.err.println("CLIENT_FACTORY_HINT: 'final exam' can only be used when a game is actively in progress.");
          return null;
        }

      case "request start case":
        if (currentClientState == ClientState.IN_LOBBY_AWAITING_START && !isHost) return new RequestStartCaseCommand();
        else if (isHost) System.err.println("Host should use 'start case'. This command is for guests.");
        else System.err.println("Not in correct state for 'request start case'.");
        return null;
      case "request final exam":
        if (currentClientState == ClientState.IN_GAME && !isHost) return new RequestInitiateExamCommand();
        else if (isHost) System.err.println("Host should use 'final exam'. This command is for guests.");
        else System.err.println("Not in correct state for 'request final exam'.");
        return null;


      // --- Standard In-Game Commands ---
      case "look": return new LookCommand();
      case "move":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: move <direction>"); return null; }
        return new MoveCommand(arg);
      case "examine":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: examine <object_name>"); return null; }
        return new ExamineCommand(arg);
      case "question":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: question <suspect_name>"); return null; }
        return new QuestionCommand(arg);
      case "journal": return new JournalCommand(arg);
      case "journal add":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: journal add <note_text>"); return null; }
        return new JournalAddCommand(arg);
      case "deduce":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: deduce <object_name>"); return null; }
        return new DeduceCommand(arg);
      case "ask watson": return new AskWatsonCommand();
      case "tasks": return new TaskCommand();
      case "submit exam answer":
        if (arg == null) { System.err.println("Usage: submit answer <q_num> <answer>"); return null; }
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) { System.err.println("Usage: submit answer <q_num> <answer>"); return null; }
        try {
          int qNum = Integer.parseInt(parts[0]);
          return new SubmitExamAnswerCommand(qNum, parts[1]);
        } catch (NumberFormatException e) {
          System.err.println("Invalid question number for 'submit answer'."); return null;
        }

        // --- General Client & Utility Commands ---
      case "/setname":
        if (arg == null || arg.isEmpty()) { System.err.println("Usage: /setname <new_display_name>"); return null; }
        return new UpdateDisplayNameCommand(new UpdateDisplayNameRequestDTO(arg));
      case "help": return new HelpCommand();
      case "exit": return new ExitCommand();
      case "add case":
        System.err.println("'add case' is not a standard client-to-server command."); return null;

      default: // Unrecognized command
        return null;
    }
  }
}