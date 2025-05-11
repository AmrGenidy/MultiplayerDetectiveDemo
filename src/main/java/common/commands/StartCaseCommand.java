package common.commands;

import JsonDTO.CaseFile;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class StartCaseCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;

  public StartCaseCommand() {
    super(false); // Does NOT require case to be started (it starts it)
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    if (context.isCaseStarted()) {
      context.sendResponseToPlayer(
          getPlayerId(), new TextMessage("The case has already started.", false));
      return;
    }
    CaseFile selectedCase = context.getSelectedCase();
    if (selectedCase == null) {
      // This might happen if the session isn't fully ready or case wasn't loaded for context
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage("Error: No case is currently selected or ready in this session.", true));
      return;
    }
    context.setCaseStarted(true);
  }

  @Override
  public String getDescription() {
    return "Begins the investigation for the selected case.";
  }
}
