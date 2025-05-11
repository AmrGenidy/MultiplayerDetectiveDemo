package common.dto;

import java.io.Serializable;
import java.util.Objects;

public class UpdateDisplayNameRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String newDisplayName;

    public UpdateDisplayNameRequestDTO(String newDisplayName) {
        this.newDisplayName = Objects.requireNonNull(newDisplayName);
    }

    public String getNewDisplayName() {
        return newDisplayName;
    }
}