package common.dto;

import java.io.Serializable;
import java.util.Objects;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JournalEntryDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String text;
    private final String contributorPlayerId;
    private final long timestamp; // Still store timestamp for sorting/display

    public JournalEntryDTO(String text, String contributorPlayerId, long timestamp) {
        this.text = Objects.requireNonNull(text, "Text cannot be null");
        this.contributorPlayerId = Objects.requireNonNull(contributorPlayerId, "Contributor ID cannot be null");
        this.timestamp = timestamp;
    }

    public String getText() { return text; }
    public String getContributorPlayerId() { return contributorPlayerId; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        // Keep timestamp in toString for display purposes
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // Maybe differentiate player notes from discoveries?
        String prefix = contributorPlayerId.startsWith("Player") ? contributorPlayerId + ":" : contributorPlayerId; // Example
        return "[" + sdf.format(new Date(timestamp)) + "] " + prefix + " " + text;
    }

    // --- MODIFIED equals ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JournalEntryDTO that = (JournalEntryDTO) o;
        // Equality based ONLY on text content and who contributed it.
        // Timestamp is ignored for uniqueness check in the Journal.
        return Objects.equals(text, that.text) &&
                Objects.equals(contributorPlayerId, that.contributorPlayerId);
    }

    // --- MODIFIED hashCode ---
    @Override
    public int hashCode() {
        // Hash code MUST use the same fields as equals().
        return Objects.hash(text, contributorPlayerId);
    }
}