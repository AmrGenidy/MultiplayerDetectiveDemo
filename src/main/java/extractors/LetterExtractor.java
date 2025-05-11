package extractors; // Move to server package structure

import Core.Letter; // Use Core Letter (if still needed)
import JsonDTO.CaseFile; // Use JSON DTO
import java.util.Objects;

/**
 * Utility class to extract introductory text (invitation, description)
 * from a CaseFile.
 * This logic might eventually be integrated directly into GameContextServer
 * or commands that need this text.
 * Intended for server-side use.
 */
public class LetterExtractor {

    private LetterExtractor() {} // Prevent instantiation

    /**
     * Populates a Letter object with data from the CaseFile.
     * Note: The use of a separate Core.Letter object might be phased out;
     * commands could just retrieve the Strings directly from the CaseFile
     * held within GameContextServer.
     *
     * @param caseFile The CaseFile DTO containing the text data.
     * @param letter   The Core.Letter object to populate.
     */
    public static void loadLetter(CaseFile caseFile, Letter letter) {
        Objects.requireNonNull(caseFile, "CaseFile cannot be null for loading letter.");
        Objects.requireNonNull(letter, "Letter object cannot be null for loading.");

        // Set fields, providing defaults if null in JSON to avoid NullPointerExceptions later
        letter.setInvitation(caseFile.getInvitation() != null ? caseFile.getInvitation() : "An invitation awaits...");
        letter.setCaseDescription(caseFile.getDescription() != null ? caseFile.getDescription() : "Details of the case are yet to be fully revealed.");
    }

    /**
     * Static helper to directly get the case description string.
     * @param caseFile The CaseFile DTO.
     * @return The case description string, or a default if null/blank.
     */
    public static String getCaseDescription(CaseFile caseFile) {
        Objects.requireNonNull(caseFile, "CaseFile cannot be null.");
        return caseFile.getDescription() != null && !caseFile.getDescription().isBlank()
                ? caseFile.getDescription()
                : "No specific case description provided.";
    }

    /**
     * Static helper to directly get the invitation string.
     * @param caseFile The CaseFile DTO.
     * @return The invitation string, or a default if null/blank.
     */
    public static String getInvitation(CaseFile caseFile) {
        Objects.requireNonNull(caseFile, "CaseFile cannot be null.");
        return caseFile.getInvitation() != null && !caseFile.getInvitation().isBlank()
                ? caseFile.getInvitation()
                : "You have been summoned to investigate a perplexing matter.";
    }
}