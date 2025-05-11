package common.dto;

import java.io.Serializable;
import java.util.Objects;

public class ExamQuestionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int questionNumber; // For ordering and reference
    private final String questionText;
    // Answer is NOT sent to client with the question

    public ExamQuestionDTO(int questionNumber, String questionText) {
        this.questionNumber = questionNumber;
        this.questionText = Objects.requireNonNull(questionText, "Question text cannot be null");
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public String getQuestionText() {
        return questionText;
    }

    @Override
    public String toString() {
        return questionNumber + ". " + questionText;
    }
}