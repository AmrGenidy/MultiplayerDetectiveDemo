package common.dto;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String senderDisplayId; // Player's chosen name or generated ID
    private final String text;
    private final long timestamp;

    public ChatMessage(String senderDisplayId, String text, long timestamp) {
        this.senderDisplayId = Objects.requireNonNull(senderDisplayId);
        this.text = Objects.requireNonNull(text);
        this.timestamp = timestamp;
    }

    public String getSenderDisplayId() { return senderDisplayId; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return "[" + sdf.format(new Date(timestamp)) + "] " + senderDisplayId + ": " + text;
    }
}