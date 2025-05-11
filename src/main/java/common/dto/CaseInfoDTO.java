package common.dto;

import java.io.Serializable;
import java.util.Objects;

public class CaseInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String title;
    private final String description;
    private final String invitation;

    public CaseInfoDTO(String title, String description, String invitation) {
        this.title = Objects.requireNonNull(title);
        this.description = Objects.requireNonNull(description);
        this.invitation = Objects.requireNonNull(invitation);
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getInvitation() { return invitation; }

    @Override
    public String toString() {
        return "CaseInfoDTO{" + "title='" + title + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CaseInfoDTO that = (CaseInfoDTO) o;
        return Objects.equals(title, that.title); // Often title is unique enough for lists
    }

    @Override
    public int hashCode() {
        return Objects.hash(title);
    }
}