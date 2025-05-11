// In common/dto/ExamResultDTO.java
package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExamResultDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final int score;
  private final int totalQuestions;
  private final String feedbackMessage;
  private final String finalRank;
  // This list will now contain strings like: "Q1: How did the killer leave...? Your Answer:
  // 'wrong1'"
  private final List<String> reviewableAnswersInfo;

  public ExamResultDTO(
      int score,
      int totalQuestions,
      String feedbackMessage,
      String finalRank,
      List<String> reviewableAnswersInfo) {
    this.score = score;
    this.totalQuestions = totalQuestions;
    this.feedbackMessage = feedbackMessage;
    this.finalRank = finalRank;
    this.reviewableAnswersInfo =
        reviewableAnswersInfo != null ? new ArrayList<>(reviewableAnswersInfo) : new ArrayList<>();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(feedbackMessage).append("\n");
    sb.append("Score: ").append(score).append("/").append(totalQuestions).append("\n");
    sb.append("Final Rank: ").append(finalRank);

    if (!reviewableAnswersInfo.isEmpty()) {
      sb.append("\n\n--- Review of Incorrect/Unanswered Questions ---");
      for (String reviewInfo : reviewableAnswersInfo) {
        // The reviewInfo string already contains Q, Your Answer, Correct Answer
        // from GameContextServer's String.format
        sb.append("\n").append(reviewInfo);
      }
    }
    return sb.toString();
  }
}
