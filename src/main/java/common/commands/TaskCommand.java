package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage;
import Core.TaskList; // Assuming TaskList is in core

import java.util.List;

public class TaskCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    public TaskCommand() {
        super(true); // Requires case to be started
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        TaskList taskList = context.getTaskList();
        if (taskList == null || taskList.getTasks().isEmpty()) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("No tasks available for this case.", false));
            return;
        }

        List<String> tasks = taskList.getTasks();
        StringBuilder taskMessage = new StringBuilder("--- Case Tasks ---\n");
        for (int i = 0; i < tasks.size(); i++) {
            taskMessage.append((i + 1)).append(". ").append(tasks.get(i)).append("\n");
        }

        // A more DTO-centric way would be to send a TaskListDTO(List<String> tasks)
        // For now, a single TextMessage:
        context.sendResponseToPlayer(getPlayerId(), new TextMessage(taskMessage.toString().trim(), false));
    }

    @Override
    public String getDescription() {
        return "Displays the list of tasks for the current case.";
    }
}