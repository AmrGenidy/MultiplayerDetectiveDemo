package singleplayer;

// My utility imports
import JsonDTO.CaseFile;
import common.commands.Command;
import common.commands.SubmitExamAnswerCommand;
import extractors.BuildingExtractor;
import extractors.CaseLoader;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import java.util.List;
import java.util.Scanner;
import singleplayer.util.CaseFileUtil;
import singleplayer.util.CommandFactorySinglePlayer;
import singleplayer.util.CommandParserSinglePlayer;

/**
 * SinglePlayerMain This class runs the whole single-player game experience. It manages the main
 * game loop, case selection, playing a case, and user input.
 */
public class SinglePlayerMain {

  // Constants
  public static final String CASES_DIRECTORY = "cases"; // Default place for my case files.
  private static final int MENU_WIDTH = 90;
  private static final String BORDER_CHAR = "═";
  private static final String CORNER_TL = "╔"; /* ... and other border chars ... */
  private static final String CORNER_TR = "╗";
  private static final String CORNER_BL = "╚";
  private static final String CORNER_BR = "╝";
  private static final String SIDE_BORDER = "║";
  private static final String T_LEFT = "╠";
  private static final String T_RIGHT = "╣";
  private static final String DIVIDER = T_LEFT + BORDER_CHAR.repeat(MENU_WIDTH) + T_RIGHT;
  private static final String TOP_BORDER = CORNER_TL + BORDER_CHAR.repeat(MENU_WIDTH) + CORNER_TR;
  private static final String BOTTOM_BORDER =
      CORNER_BL + BORDER_CHAR.repeat(MENU_WIDTH) + CORNER_BR;

  // My game state and input
  private GameContextSinglePlayer gameContext;
  private Scanner scanner; // For reading user input.

  public SinglePlayerMain() {
    this.gameContext = new GameContextSinglePlayer(); // My SP game brain.
    this.scanner = new Scanner(System.in); // Read from standard console.
  }

  /** Main game loop: allows selecting and playing multiple cases until user quits. */
  public void runGame() {
    // System.out.println("Welcome to the Single Player Detective Game!"); // MainLauncher shows
    // this.

    while (!gameContext.wantsToExitApplication()) {
      gameContext.resetExitFlags(); // Fresh start for exit decisions.
      displayCaseSelectionMenu(); // Show pretty menu.
      CaseFile selectedCase = selectCase(); // Let user pick or add/quit.

      if (selectedCase == null) { // Means user chose to quit or add case.
        if (gameContext.wantsToExitApplication()) {
          break; // Break from while loop to exit runGame().
        }
        // If not exiting app, it was 'add case', so just 'continue' to refresh menu.
        continue;
      }
      // If a case was selected, initialize and play it.
      initializeAndPlayCase(selectedCase);
    }
    System.out.println("\nThank you for playing Single Player. Goodbye!");
    // Don't close 'this.scanner' if it wraps System.in and MainLauncher might use System.in later.
  }

  /** Displays the formatted case selection menu. */
  private void displayCaseSelectionMenu() {
    List<CaseFile> cases = CaseLoader.loadCases(CASES_DIRECTORY); // Get current cases.

    System.out.println("\n" + TOP_BORDER);
    String title = "SELECT A CASE TO INVESTIGATE";
    int padding = (MENU_WIDTH - title.length()) / 2;
    System.out.println(
        SIDE_BORDER
            + " ".repeat(Math.max(0, padding))
            + title
            + " ".repeat(Math.max(0, MENU_WIDTH - title.length() - padding))
            + SIDE_BORDER);
    System.out.println(DIVIDER);

    if (cases.isEmpty()) {
      String noCasesMsg =
          "No cases found in '" + CASES_DIRECTORY + "'. Use 'add case [path]' or 'quit'.";
      int msgPadding = (MENU_WIDTH - noCasesMsg.length()) / 2;
      System.out.printf(
          "%s %-" + MENU_WIDTH + "s %s%n",
          SIDE_BORDER,
          " ".repeat(Math.max(0, msgPadding)) + noCasesMsg,
          SIDE_BORDER);
    } else {
      for (int i = 0; i < cases.size(); i++) {
        String caseLine = String.format("%d. %s", i + 1, cases.get(i).getTitle());
        System.out.printf("%s %-" + MENU_WIDTH + "s %s%n", SIDE_BORDER, caseLine, SIDE_BORDER);
      }
    }
    System.out.println(BOTTOM_BORDER);
  }

  /**
   * Handles user input for selecting a case, adding a new case, or quitting.
   *
   * @return The selected CaseFile, or null if user quits or chose 'add case'.
   */
  private CaseFile selectCase() {
    List<CaseFile> cases = CaseLoader.loadCases(CASES_DIRECTORY); // Get up-to-date list.
    while (true) {
      System.out.print("Enter case number (0 to add case, 'quit' to exit game): ");
      String input = scanner.nextLine().trim();

      if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
        // Tell context player wants to fully exit the application.
        gameContext.handlePlayerExitRequest(gameContext.getPlayerDetective(null).getPlayerId());
        return null;
      }

      if (input.equals("0") || input.toLowerCase().startsWith("add case")) {
        handleAddingCase(input); // Let helper method do the add logic.
        return null; // Return null to signal main loop to re-display menu.
      }

      try {
        int choice = Integer.parseInt(input);
        if (choice > 0 && choice <= cases.size()) {
          return cases.get(choice - 1); // Valid case number selected.
        } else {
          System.out.println("Invalid choice. Not in the list.");
        }
      } catch (NumberFormatException e) {
        System.out.println(
            "Invalid input. Please enter a number, or a command like 'add case ...' or 'quit'.");
      }
    }
  }

  /**
   * Handles the logic for adding a new case file via user input. Calls CaseFileUtil to perform the
   * actual file operations.
   */
  private void handleAddingCase(String initialInput) {
    String filePath = "";
    // Extract filepath if provided with "add case filepath"
    if (initialInput.toLowerCase().startsWith("add case")) {
      if (initialInput.length() > "add case ".length()) {
        filePath = initialInput.substring("add case ".length()).trim();
      }
    }
    // If input was "0" or just "add case", prompt for path.
    if (filePath.isEmpty()) {
      System.out.print("Enter the full file path to the case JSON: ");
      filePath = scanner.nextLine().trim();
      if (filePath.isEmpty()) {
        System.out.println("Add case cancelled: No file path provided.");
        return; // User cancelled by providing no path.
      }
    }

    // Call my utility to do the heavy lifting for adding the file.
    // CaseFileUtil will print its own success/error messages.
    CaseFileUtil.addCaseFromFile(filePath);
  }

  /** Initializes the game context for a selected case and starts the play loop for it. */
  private void initializeAndPlayCase(CaseFile caseFile) {
    if (caseFile == null) return;

    System.out.println("\nLoading case: " + caseFile.getTitle() + "...");
    gameContext.resetForNewCaseLoad(); // Reset context for the new case.

    boolean loadingSuccess = true;
    try {
      // Use my extractors to populate the game context.
      if (!BuildingExtractor.loadBuilding(caseFile, gameContext)) {
        System.out.println("Error: Failed to load building from case file.");
        loadingSuccess = false;
      } else {
        GameObjectExtractor.loadObjects(caseFile, gameContext);
        SuspectExtractor.loadSuspects(caseFile, gameContext);
      }
    } catch (Exception e) { // Catch any unexpected error during extraction.
      System.out.println(
          "CRITICAL_LOAD_ERROR for '" + caseFile.getTitle() + "': " + e.getMessage());
      e.printStackTrace(); // Important for dev to see what went wrong.
      loadingSuccess = false;
    }

    if (!loadingSuccess) {
      System.out.println(
          "Failed to load case '"
              + caseFile.getTitle()
              + "' completely. Returning to case selection.");
      return;
    }

    // Final context setup with case data.
    gameContext.initializeNewCase(caseFile, caseFile.getStartingRoom());

    // Display the case intro.
    System.out.println("\n--- Case Invitation ---");
    System.out.println(caseFile.getInvitation());
    System.out.println("\nType 'start case' to begin, or 'exit' to return to case selection.");

    playCurrentCase(); // Enter the main gameplay loop for this case.
  }

  /**
   * The main gameplay loop for an active case. Handles player input, command execution, and exam
   * Q&A flow.
   */
  private void playCurrentCase() {
    while (!gameContext.wantsToExitToCaseSelection() && !gameContext.wantsToExitApplication()) {
      String prompt = "> "; // Default prompt.
      if (gameContext.isAwaitingExamAnswer()) {
        prompt = ""; // No extra prompt char when answering exam question.
      } else if (gameContext.isCaseStarted() && gameContext.getCurrentRoomForPlayer(null) != null) {
        prompt = "<" + gameContext.getCurrentRoomForPlayer(null).getName() + "> ";
      }

      if (!prompt.isEmpty()) System.out.print(prompt);

      String input = scanner.nextLine().trim();
      if (input.isEmpty()) continue; // Ignore empty lines.

      Command commandToExecute;
      if (gameContext.isAwaitingExamAnswer()) {
        // If answering exam, input is the answer. Directly create SubmitExamAnswerCommand.
        commandToExecute =
            new SubmitExamAnswerCommand(
                gameContext.getAwaitingQuestionNumber(), // Context knows which question.
                input // Raw input is the answer.
                );
      } else {
        // Standard command parsing.
        String[] parsedInput = CommandParserSinglePlayer.parseInputSimple(input);
        commandToExecute = CommandFactorySinglePlayer.createCommand(parsedInput);
      }

      if (commandToExecute != null) {
        if (gameContext.getPlayerDetective(null) != null) { // Make sure detective is there.
          commandToExecute.setPlayerId(gameContext.getPlayerDetective(null).getPlayerId());
          commandToExecute.execute(gameContext); // Execute command using SP context.
        } else {
          System.out.println("SP_ERROR: Player detective not initialized. Cannot execute command.");
        }
      } else {
        // Only show "Unknown command" if not in exam Q&A.
        if (!gameContext.isAwaitingExamAnswer()) {
          System.out.println("Unknown command. Type 'help' for available commands.");
        } else {
          // If awaiting exam answer and command was null (shouldn't happen with direct creation).
          System.out.println("Please type your answer for the question.");
        }
      }
    } // End of in-case loop.

    // After loop, if exiting to case selection, print message.
    if (gameContext.wantsToExitToCaseSelection()) {
      if (gameContext.isCaseStarted()) { // Ensure case flag is reset.
        gameContext.setCaseStarted(false);
      }
      System.out.println("\nReturning to case selection menu...");
    }
  }

  /** Entry point for running the Single Player game directly. */
  public static void main(String[] args) {
    SinglePlayerMain game = new SinglePlayerMain();
    try {
      game.runGame();
    } catch (Exception e) { // Catch-all for the main game flow.
      System.err.println("\nUNEXPECTED SP_ERROR: An unhandled error occurred in SinglePlayerMain:");
      e.printStackTrace();
      System.err.println("Application will now exit.");
    }
  }
}
