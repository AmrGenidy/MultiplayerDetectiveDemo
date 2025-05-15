package client;

import common.NetworkConstants;

public class ClientMain {

  /** Entry point for the client application. */
  public static void main(String[] args) {
    // Default connection settings.
    String host = NetworkConstants.DEFAULT_HOST;
    int port = NetworkConstants.DEFAULT_PORT;

    // Override defaults if command-line arguments are provided.
    if (args.length >= 1) {
      host = args[0];
    }
    if (args.length >= 2) {
      try {
        port = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        System.err.println(
            "Invalid port number provided: '" + args[1] + "'. Using default port " + port + ".");
      }
    }

    // Display startup banner.
    System.out.println("\n========================================");
    System.out.println("  Starting Detective Game Client...");
    System.out.println("========================================");

    // Create and start the main client logic in a separate thread.
    GameClient client = new GameClient(host, port);
    Thread clientMainLogicThread = new Thread(client, "GameClient-MainLogic");
    clientMainLogicThread.start();

    // Wait for the client logic thread to finish.
    try {
      clientMainLogicThread.join();
    } catch (InterruptedException e) {
      System.err.println("ClientMain was interrupted while waiting for GameClient to finish.");
      Thread.currentThread().interrupt(); // Re-set interrupt status.
    }

    // Exit message after client logic completes.
    System.out.println("ClientMain: GameClient has finished its execution.");
  }
}
