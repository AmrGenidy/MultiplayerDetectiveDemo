package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class HostGameRequestDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String caseTitle;
  private final boolean isPublic;

  // Could add player preferences like desiredPlayerName later

  public HostGameRequestDTO(String caseTitle, boolean isPublic) {
    this.caseTitle = Objects.requireNonNull(caseTitle);
    this.isPublic = isPublic;
  }

  public String getCaseTitle() {
    return caseTitle;
  }

  public boolean isPublic() {
    return isPublic;
  }

  @Override
  public String toString() {
    return "HostGameRequestDTO{"
        + "caseTitle='"
        + caseTitle
        + '\''
        + ", isPublic="
        + isPublic
        + '}';
  }
}
