package server;

public enum GameSessionState {
    WAITING_FOR_PLAYERS, // Lobby is open, waiting for a second player
    ACTIVE,              // Game is in progress with two players
    ENDED_NORMAL,        // Game concluded normally (e.g., case solved)
    ENDED_ABANDONED,     // Game ended because a player left or disconnected
    LOADING,             // Session is being created and case data loaded
    IN_LOBBY_AWAITING_START, ERROR                // Session encountered an unrecoverable error
}