package common.dto;

import java.io.Serializable;
import java.util.Objects;

public class PublicGameInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String hostPlayerDisplayId; // Could be a chosen name or generated
    private final String caseTitle;
    private final String sessionId; // To join this specific game lobby

    public PublicGameInfoDTO(String hostPlayerDisplayId, String caseTitle, String sessionId) {
        this.hostPlayerDisplayId = Objects.requireNonNull(hostPlayerDisplayId);
        this.caseTitle = Objects.requireNonNull(caseTitle);
        this.sessionId = Objects.requireNonNull(sessionId);
    }

    public String getHostPlayerDisplayId() { return hostPlayerDisplayId; }
    public String getCaseTitle() { return caseTitle; }
    public String getSessionId() { return sessionId; }

    @Override
    public String toString() {
        return "PublicGameInfoDTO{" + "host='" + hostPlayerDisplayId + '\'' + ", case='" + caseTitle + '\'' + ", sessionId='" + sessionId + '\'' + '}';
    }
}