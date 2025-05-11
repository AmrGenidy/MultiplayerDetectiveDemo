package client;

import common.NetworkConstants; // My network defaults, good.

public class ClientMain {

    /**
     * Entry point for the client application.
     * Parses optional host/port args, creates a GameClient,
     * and runs its main logic in a new thread, waiting for it to complete.
     */
    public static void main(String[] args) {
        // Default connection settings.
        String host = NetworkConstants.DEFAULT_HOST;
        int port = NetworkConstants.DEFAULT_PORT;

        // Check for command-line arguments to override defaults.
        // Arg 0: host, Arg 1: port.
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                // Oops, bad port number. Tell user, but keep going with default.
                System.err.println("Invalid port number provided: '" + args[1] + "'. Using default port " + port + ".");
            }
        }

        // Simple startup banner.
        System.out.println("\n========================================");
        System.out.println("  Starting Detective Game Client...");
        System.out.println("========================================");

        // Create the main client logic object.
        GameClient client = new GameClient(host, port);

        // GameClient implements Runnable, so its run() method contains the main client loop.
        // Need to run it in a separate thread so this main() method can wait (join).
        Thread clientMainLogicThread = new Thread(client, "GameClient-MainLogic");
        clientMainLogicThread.start(); // Kick off GameClient.run()

        try {
            // This is important: make the ClientMain thread wait here until
            // the GameClient-MainLogic thread (i.e., client.run()) finishes.
            // Without this, ClientMain.main() would exit immediately, and
            // MainLauncher would think the client "mode" is done too soon.
            clientMainLogicThread.join();
        } catch (InterruptedException e) {
            // This can happen if something interrupts ClientMain's thread while it's waiting.
            System.err.println("ClientMain was interrupted while waiting for GameClient to finish.");
            Thread.currentThread().interrupt(); // Good practice to re-set interrupt status.
        }

        // This line is reached only after GameClient.run() has completed (e.g., user quit).
        System.out.println("ClientMain: GameClient has finished its execution.");
    }
}