package singleplayer;

import singleplayer.util.CaseFileUtil;
import singleplayer.util.CommandParserSinglePlayer; // Assuming you have these
import singleplayer.util.CommandFactorySinglePlayer; // Assuming you have these
import common.commands.Command;
import common.dto.ExamQuestionDTO;
import common.commands.SubmitExamAnswerCommand;
import JsonDTO.CaseFile;
import extractors.CaseLoader;
import extractors.BuildingExtractor;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
// import common.commands.AddCaseCommand; // If using the static method approach for SP

import java.util.List;
import java.util.Scanner;

public class SinglePlayerMain {

    public static final String CASES_DIRECTORY = "cases";
    private GameContextSinglePlayer gameContext;
    private Scanner scanner;

    // Constants for menu formatting
    private static final int MENU_WIDTH = 90; // Adjust width as needed
    private static final String BORDER_CHAR = "═";
    private static final String CORNER_TL = "╔";
    private static final String CORNER_TR = "╗";
    private static final String CORNER_BL = "╚";
    private static final String CORNER_BR = "╝";
    private static final String SIDE_BORDER = "║";
    private static final String T_LEFT = "╠";
    private static final String T_RIGHT = "╣";
    private static final String DIVIDER = T_LEFT + BORDER_CHAR.repeat(MENU_WIDTH) + T_RIGHT;
    private static final String TOP_BORDER = CORNER_TL + BORDER_CHAR.repeat(MENU_WIDTH) + CORNER_TR;
    private static final String BOTTOM_BORDER = CORNER_BL + BORDER_CHAR.repeat(MENU_WIDTH) + CORNER_BR;


    public SinglePlayerMain() {
        this.gameContext = new GameContextSinglePlayer();
        this.scanner = new Scanner(System.in);
    }

    public void runGame() {
        System.out.println("Welcome to the Single Player Detective Game!");

        while (!gameContext.wantsToExitApplication()) {
            gameContext.resetExitFlags();
            displayCaseSelectionMenu(); // Display the formatted menu
            CaseFile selectedCase = selectCase(); // Get user choice

            if (selectedCase == null) {
                if (gameContext.wantsToExitApplication()) break;
                // If selectCase returns null without setting exit, it means 'add case' was chosen
                // and handled, so we just loop back to display the menu again.
                continue;
            }

            // Proceed to play the selected case
            initializeAndPlayCase(selectedCase);
        }
        System.out.println("Thank you for playing. Goodbye!");
        // Avoid closing System.in scanner here if MainLauncher is used
        // scanner.close();
    }

    // --- MODIFIED MENU DISPLAY ---
    private void displayCaseSelectionMenu() {
        List<CaseFile> cases = CaseLoader.loadCases(CASES_DIRECTORY);

        System.out.println("\n" + TOP_BORDER);
        // Center the title (approximate)
        String title = "SELECT A CASE TO INVESTIGATE";
        int padding = (MENU_WIDTH - title.length()) / 2;
        System.out.println(SIDE_BORDER + " ".repeat(padding) + title + " ".repeat(MENU_WIDTH - title.length() - padding) + SIDE_BORDER);
        System.out.println(DIVIDER);

        if (cases.isEmpty()) {
            String noCasesMsg = "No cases available in '" + CASES_DIRECTORY + "'. Use 'add case [path]' or 'quit'.";
            int msgPadding = (MENU_WIDTH - noCasesMsg.length()) / 2;
            if (msgPadding < 0) msgPadding = 0; // prevent negative padding
            System.out.printf("%s %-" + MENU_WIDTH + "s %s%n", SIDE_BORDER, " ".repeat(msgPadding) + noCasesMsg, SIDE_BORDER);
        } else {
            for (int i = 0; i < cases.size(); i++) {
                String caseLine = String.format("%d. %s", i + 1, cases.get(i).getTitle());
                // Pad the line to fit the width
                System.out.printf("%s %-" + MENU_WIDTH + "s %s%n", SIDE_BORDER, caseLine, SIDE_BORDER);
            }
        }
        System.out.println(BOTTOM_BORDER);
    }

    // --- MODIFIED PROMPT ---
    private CaseFile selectCase() {
        List<CaseFile> cases = CaseLoader.loadCases(CASES_DIRECTORY); // Reload just in case add case worked
        while (true) {
            // Adjusted prompt to match desired output
            System.out.print("Enter case number (0 to add case, 'quit' to exit game): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
                gameContext.handlePlayerExitRequest(gameContext.getPlayerDetective(null).getPlayerId());
                return null; // Signal exit application
            }

            if (input.equals("0") || input.toLowerCase().startsWith("add case")) {
                handleAddingCase(input);
                return null; // Signal to loop back and redisplay menu after adding
            }

            try {
                int choice = Integer.parseInt(input);
                if (choice > 0 && choice <= cases.size()) {
                    return cases.get(choice - 1); // Return the selected case DTO
                } else if (choice == 0) { // User typed 0 directly after menu display
                    handleAddingCase("0"); // Treat 0 as triggering the add case flow
                    return null; // Signal to loop back and redisplay menu
                } else {
                    System.out.println("Invalid choice. Please select a valid number from the list, 0, or 'quit'.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number, 0, 'add case [path]', or 'quit'.");
            }
        }
    }

    // Helper method to handle the add case logic
    private void handleAddingCase(String input) {
        String filePath = "";
        if (input.toLowerCase().startsWith("add case")) {
            if (input.length() > "add case ".length()) {
                filePath = input.substring("add case ".length()).trim();
            }
        }
        // If input was "0" or "add case" without path, prompt for path
        if (filePath.isEmpty()) {
            System.out.print("Enter the full file path to the case JSON: ");
            filePath = scanner.nextLine().trim();
            if (filePath.isEmpty()) {
                System.out.println("Add case cancelled: No file path provided.");
                return; // Cancelled add case
            }
        }

        // --- Call the ACTUAL Add Case Logic ---
        try {
            System.out.println("Attempting to add case from: " + filePath);

            // *** REPLACE SIMULATION WITH ACTUAL CALL ***
            // Assuming CaseFileUtil has the static methods from your original AddCaseCommand
            CaseFileUtil.addCaseFromFile(filePath);
            // The addCaseFromFile method itself prints success/error messages.
            // If it doesn't, you can add a generic success message here,
            // but it's better if the utility handles its own detailed feedback.

            // System.out.println("Case added successfully. The menu will refresh."); // This might be redundant if addCaseFromFile prints success

        } catch (Exception e) { // Catch any exceptions from addCaseFromFile
            System.out.println("Error adding case: " + e.getMessage());
            // Optionally print stack trace for debugging if addCaseFromFile doesn't.
            // e.printStackTrace();
        }
        // No need to manually redisplay menu here;
        // selectCase returning null triggers loop in runGame() which calls displayCaseSelectionMenu()
    }


    private void initializeAndPlayCase(CaseFile caseFile) {
        if (caseFile == null) return; // Should not happen if called correctly

        System.out.println("\nLoading case: " + caseFile.getTitle() + "...");
        gameContext.resetForNewCaseLoad(); // Use a method in GameContextSinglePlayer to reset state

        // --- Use Extractors ---
        boolean success = true;
        try {
            if (!BuildingExtractor.loadBuilding(caseFile, gameContext)) {
                System.out.println("Error: Failed to load building."); success = false;
            } else {
                GameObjectExtractor.loadObjects(caseFile, gameContext);
                SuspectExtractor.loadSuspects(caseFile, gameContext);
            }
        } catch (Exception e) {
            System.out.println("An critical error occurred during case loading for '" + caseFile.getTitle() + "': " + e.getMessage());
            e.printStackTrace(); // Log details for debugging
            success = false;
        }

        if (!success) {
            System.out.println("Failed to load case '" + caseFile.getTitle() + "' completely. Returning to case selection.");
            return;
        }

        // Initialize the rest of the game context with the loaded case data
        gameContext.initializeNewCase(caseFile, caseFile.getStartingRoom());

        System.out.println("\n--- Case Invitation ---");
        System.out.println(caseFile.getInvitation());
        System.out.println("\nType 'start case' to begin the investigation, or 'exit' to return to case selection.");

        // --- In-Case Game Loop ---
        playCurrentCase(); // Encapsulate the inner loop

    }

    private void playCurrentCase() {
        while (!gameContext.wantsToExitToCaseSelection() && !gameContext.wantsToExitApplication()) {
            String prompt = "> "; // Default prompt
            if (gameContext.isCaseStarted() && gameContext.getCurrentRoomForPlayer(null) != null && !gameContext.isAwaitingExamAnswer()) {
                prompt = "<" + gameContext.getCurrentRoomForPlayer(null).getName() + "> ";
            } else if (gameContext.isAwaitingExamAnswer()){
                // No prompt needed here, the question displayed by context acts as prompt
                prompt = ""; // Empty prompt
            }

            if (!prompt.isEmpty()) System.out.print(prompt); // Print prompt only if needed

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            Command commandToExecute = null;

            if (gameContext.isAwaitingExamAnswer()) {
                commandToExecute = new SubmitExamAnswerCommand(
                        gameContext.getAwaitingQuestionNumber(),
                        input
                );
            } else {
                // Use your single player parser and factory
                // Adapt based on whether your SP factory expects String[] or ParsedCommandData
                String[] parsedInput = CommandParserSinglePlayer.parseInputSimple(input); // Assuming this exists
                commandToExecute = CommandFactorySinglePlayer.createCommand(parsedInput); // Assuming this exists
            }

            if (commandToExecute != null) {
                // Common command interface requires player ID, get it from SP context
                if(gameContext.getPlayerDetective(null) != null) { // Check if detective is initialized
                    commandToExecute.setPlayerId(gameContext.getPlayerDetective(null).getPlayerId());
                    commandToExecute.execute(gameContext);
                } else {
                    System.out.println("Error: Player context not initialized.");
                    // This indicates an issue in initializeNewCase or context state.
                }
            } else {
                // Only print unknown command if not awaiting exam answer
                if(!gameContext.isAwaitingExamAnswer()) {
                    System.out.println("Unknown command. Type 'help' for available commands.");
                } else {
                    // If awaiting exam answer and command was null, maybe re-prompt?
                    System.out.println("Invalid input format for exam answer. Please just type your answer.");
                }
            }
        } // End of in-case game loop

        // Handle exit reason
        if (gameContext.wantsToExitToCaseSelection()) {
            if (gameContext.isCaseStarted()) {
                gameContext.setCaseStarted(false); // Ensure case state is reset
            }
            System.out.println("\nReturning to case selection menu...");
        }
    }


    public static void main(String[] args) {
        // System.out.println("Starting Detective Game - Single Player Mode..."); // Header moved to MainLauncher
        SinglePlayerMain game = new SinglePlayerMain();
        try {
            game.runGame(); // This method blocks until the single player game is exited
        } catch (Exception e) {
            System.err.println("\nAn unexpected error occurred in the single player game:");
            e.printStackTrace();
            System.err.println("Exiting application due to error.");
        }
        // System.out.println("Exiting Single Player Mode..."); // Header moved to MainLauncher
    }
}