package common.dto;

import java.io.Serializable;

public class TextMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String text;
    private final boolean isError;
    // Could add a MessageType enum later (e.g., INFO, ERROR, HINT, DIALOGUE)

    public TextMessage(String text, boolean isError) {
        this.text = text;
        this.isError = isError;
    }

    public String getText() { return text; }
    public boolean isError() { return isError; }

    @Override
    public String toString() {
        return (isError ? "[ERROR] " : "") + text;
    }
}