package launcher;

import client.ClientMain;
import server.ServerMain;
import singleplayer.SinglePlayerMain;

import java.util.Scanner;

public class MainLauncher {

    public static void main(String[] args) {
        String[] passThroughArgs = args;
        Scanner scanner = new Scanner(System.in);
        boolean exitLauncher = false;

        printHeader("Welcome to the Text-Based Detective Game");

        while (!exitLauncher) {
            System.out.println("\nChoose an option:");
            System.out.println("  1. Start Single Player Game");
            System.out.println("  2. Join/Host Multiplayer Game (Start Client)");
            System.out.println("  3. Start Game Server");
            System.out.println("  4. Exit Application");
            System.out.print("Enter your choice (1-4): ");

            String choice = "";
            if (scanner.hasNextLine()){ // Check if there's input to prevent NoSuchElementException
                choice = scanner.nextLine().trim();
            } else {
                System.out.println("No input detected, exiting launcher.");
                exitLauncher = true; // Exit if scanner closes
                continue;
            }


            switch (choice) {
                case "1":
                    printHeader("Launching Single Player Mode");
                    try {
                        SinglePlayerMain.main(passThroughArgs); // This will now block
                    } catch (Exception e) {
                        handleModeError("Single Player", e);
                    }
                    printHeader("Returned from Single Player Mode");
                    break;

                case "2":
                    // ClientMain itself prints its startup message
                    try {
                        ClientMain.main(passThroughArgs); // This will now block
                    } catch (Exception e) {
                        handleModeError("Multiplayer Client", e);
                    }
                    printHeader("Returned from Multiplayer Client");
                    break;

                case "3":
                    printHeader("Launching Game Server");
                    try {
                        ServerMain.main(passThroughArgs); // ServerMain manages its own threads and console
                        // It will block on its console input.
                    } catch (Exception e) {
                        handleModeError("Game Server", e);
                    }
                    printHeader("Returned from Game Server process"); // When ServerMain's console loop ends
                    break;

                case "4":
                    System.out.println("\nExiting application. Goodbye!");
                    exitLauncher = true;
                    break;

                default:
                    System.out.println("\nInvalid choice. Please enter a number between 1 and 4.");
                    break;
            }
            // The "Press Enter to return" is removed for simplicity with blocking calls.
            // The menu will reappear after a mode fully exits and returns here.
        }

        // scanner.close(); // Generally avoid closing System.in
        System.out.println("Application launcher has terminated.");
    }

    private static void printHeader(String title) {
        System.out.println("\n========================================");
        System.out.println("  " + title);
        System.out.println("========================================");
    }

    private static void handleModeError(String modeName, Exception e) {
        System.err.println("\nError running " + modeName + " mode: " + e.getMessage());
        // Ask user if they want to see full trace for debugging
        System.err.print("Show full stack trace? (y/N): ");
        Scanner errScanner = new Scanner(System.in); // Use a temp scanner for this
        String showTrace = errScanner.nextLine().trim();
        if (showTrace.equalsIgnoreCase("y")) {
            e.printStackTrace();
        }
        // errScanner.close(); // Avoid closing System.in
    }
}