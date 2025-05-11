package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class UpdateDisplayNameRequestDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String newDisplayName;

  public UpdateDisplayNameRequestDTO(String newDisplayName) {
    this.newDisplayName = Objects.requireNonNull(newDisplayName);
  }

  public String getNewDisplayName() {
    return newDisplayName;
  }
}
