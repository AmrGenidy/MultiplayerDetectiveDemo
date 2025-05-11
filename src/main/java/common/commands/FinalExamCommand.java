package common.commands;

import common.interfaces.GameActionContext;
import common.dto.TextMessage;
import JsonDTO.CaseFile; // For getting total question count, though context should handle this

import java.util.List; // Not directly used, but CaseFile.getFinalExam() returns List
import java.util.Map;
import java.util.HashMap;

/**
 * FinalExamCommand
 * Represents the player submitting all their answers for the final exam at once.
 * The client application would be responsible for collecting all answers before creating this command.
 */
public class FinalExamCommand extends BaseCommand {
    private static final long serialVersionUID = 1L;

    // Stores all answers: Question Text (or ID) -> Player's Answer Text
    private final Map<String, String> answers;

    /**
     * Constructor for submitting a batch of final exam answers.
     * @param answers A map where keys are question identifiers (e.g., question text or number)
     *                and values are the player's answers.
     */
    public FinalExamCommand(Map<String, String> answers) {
        super(true); // Requires the case to be started.
        // Create a defensive copy of the answers map.
        this.answers = (answers != null) ? new HashMap<>(answers) : new HashMap<>();
    }

    /**
     * Gets the map of answers submitted by the player.
     * @return A copy of the answers map.
     */
    public Map<String, String> getAnswers() {
        return new HashMap<>(answers); // Return a copy for immutability.
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // First, check if the player is even allowed to attempt/submit the exam now.
        if (!context.canStartFinalExam(getPlayerId())) { // `canStartFinalExam` also implies can *submit* for this model
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("You cannot submit final exam answers at this time.", true));
            return;
        }

        // Check if any answers were actually provided.
        if (this.answers.isEmpty()) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("No answers provided for the final exam submission.", true));
            return;
        }

        // Delegate the answer processing and scoring to the context.
        // The context.submitFinalExamAnswers method is responsible for:
        // 1. Retrieving the actual exam questions and correct answers.
        // 2. Comparing submitted answers against correct ones.
        // 3. Calculating the score.
        // 4. Updating the player's detective object (score, rank).
        // 5. Sending an ExamResultDTO (or multiple TextMessages) back to the player(s).
        int score = context.submitFinalExamAnswers(getPlayerId(), this.answers);

        // --- The following logic for sending results is now ideally handled by ---
        // --- the context's implementation of submitFinalExamAnswers          ---
        // --- It would typically send a structured ExamResultDTO.             ---
        // --- Keeping this here as a fallback or if context is very minimal.  ---

        CaseFile selectedCase = context.getSelectedCase();
        if (selectedCase == null || selectedCase.getFinalExam() == null) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("Error: Exam configuration missing on server.", true));
            return;
        }
        int totalQuestions = selectedCase.getFinalExam().size();

        if (totalQuestions == 0 && !this.answers.isEmpty()) {
            // Submitted answers but no questions configured.
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("No final exam questions seem to be configured for this case, but answers were submitted.", true));
            return;
        } else if (totalQuestions == 0 && this.answers.isEmpty()) {
            // No questions, no answers.
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("No final exam questions configured for this case.", false));
            return;
        }


        // This result formatting should ideally be done based on what submitFinalExamAnswers returns
        // or by the ExamResultDTO sent by the context.
        // If submitFinalExamAnswers in context sends the detailed ExamResultDTO, these lines are redundant.
        String resultMessage;
        if (score == totalQuestions) {
            resultMessage = "Congratulations! You solved the case perfectly.";
        } else if (score >= 0) { // score can't be less than 0. Check if any progress made.
            resultMessage = "Exam submitted. Score: " + score + "/" + totalQuestions + ".";
        } else { // Should not happen if score is calculated correctly.
            resultMessage = "Exam submitted. There was an issue calculating your score.";
        }
        context.sendResponseToPlayer(getPlayerId(), new TextMessage(resultMessage, false));

        if (context.getPlayerDetective(getPlayerId()) != null) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("Your final rank for this case: " + context.getPlayerDetective(getPlayerId()).getRank(), false));
        }
    }

    @Override
    public String getDescription() {
        return "Submits all answers for the final exam to solve the case.";
    }
}