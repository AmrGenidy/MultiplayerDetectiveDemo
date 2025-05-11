package client;

/**
 * ClientState Represents the different states the game client can be in. This helps manage UI (what
 * menus/prompts to show) and what actions are valid.
 */
public enum ClientState {
  // Connection States
  DISCONNECTED, // Not connected, or connection lost and not retrying.
  CONNECTING, // Actively trying initial connection.
  RECONNECTING, // Actively trying to reconnect after a drop.

  // Lobby / Main Menu States (after successful connection)
  CONNECTED_IDLE, // At the main menu: Host, Join, etc.

  // Hosting Game Flow
  SELECTING_HOST_TYPE, // Choosing: Public or Private host.
  REQUESTING_CASE_LIST_FOR_HOST, // Waiting for server to send available cases (after selecting host
  // type).
  SELECTING_HOST_CASE, // Showing cases to host, user picks one.
  SENDING_HOST_REQUEST, // Waiting for server response after sending HostGameCommand.
  HOSTING_LOBBY_WAITING, // Host successful, lobby created, waiting for Player 2.

  // Joining Game Flow
  SELECTING_JOIN_TYPE, // Choosing: Join Public or Join Private.
  REQUESTING_PUBLIC_GAMES, // Waiting for server to send list of public games.
  VIEWING_PUBLIC_GAMES, // Showing public games, user picks one.
  SENDING_JOIN_PUBLIC_REQUEST, // Waiting for server response after sending JoinPublicGameCommand.
  ENTERING_PRIVATE_CODE, // Prompting user for a private game code.
  SENDING_JOIN_PRIVATE_REQUEST, // Waiting for server response after sending JoinPrivateGameCommand.

  // In-Session / In-Game States
  IN_LOBBY_AWAITING_START, // Both players in session, ready for 'start case' command.
  IN_GAME, // Case has started, actively playing.

  // Exam Flow States (primarily for host)
  ATTEMPTING_FINAL_EXAM, // Host sent InitiateFinalExam, waiting for first question DTO.
  ANSWERING_FINAL_EXAM_Q, // Host received a question, currently typing answer.
  SUBMITTING_EXAM_ANSWER, // Host sent an answer, waiting for next question or results DTO.
  VIEWING_EXAM_RESULT, // Exam results (ExamResultDTO) received and displayed. (Transient state)

  // General "Waiting for Server" State (if a more generic one is needed beyond specific SENDING_*
  // states)
  // AWAITING_SERVER_RESPONSE, // Could be used for generic waits if specific SENDING_* states are
  // too many.
  // Currently, SENDING_* states serve this purpose.

  // Terminal State
  EXITING // Client is shutting down.
}
