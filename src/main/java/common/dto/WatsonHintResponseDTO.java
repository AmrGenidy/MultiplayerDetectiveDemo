package common.dto;

import java.io.Serializable;

public class WatsonHintResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String message;
    private final boolean isActualHint; // True if message is a hint, false if it's a status/error

    public WatsonHintResponseDTO(String message, boolean isActualHint) {
        this.message = message;
        this.isActualHint = isActualHint;
    }

    public String getMessage() { return message; }
    public boolean isActualHint() { return isActualHint; }
}