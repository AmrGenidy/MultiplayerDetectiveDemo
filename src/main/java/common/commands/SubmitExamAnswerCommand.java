package common.commands;

import common.interfaces.GameActionContext;
// No need for specific context imports like GameContextSinglePlayer anymore

public class SubmitExamAnswerCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;
    private final int questionNumber;
    private final String answerText;

    public SubmitExamAnswerCommand(int questionNumber, String answerText) {
        super(true); // Requires case to be started (and exam initiated)
        this.questionNumber = questionNumber;
        this.answerText = (answerText != null) ? answerText.trim() : "";
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public String getAnswerText() {
        return answerText;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // Now that processExamAnswer is part of the GameActionContext interface,
        // we can call it directly without type checking/casting.
        context.processExamAnswer(getPlayerId(), this.questionNumber, this.answerText);

        // The context's implementation of processExamAnswer (in GameContextServer or GameContextSinglePlayer)
        // is now responsible for:
        // 1. Storing the answer.
        // 2. Incrementing its internal question index.
        // 3. Calling its internal method to send the next question (e.g., sendNextExamQuestionToSession)
        //    OR calling its internal method to evaluate and send final results if all questions are done.
    }

    @Override
    public String getDescription() {
        return "Submits an answer to a specific final exam question. (Used during interactive exam)";
    }
}