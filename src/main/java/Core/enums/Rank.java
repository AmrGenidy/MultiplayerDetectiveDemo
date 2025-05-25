package Core.enums;

public enum Rank {
    JUNIOR_INVESTIGATOR("Junior Investigator"),
    INTERMEDIATE_INVESTIGATOR("Intermediate Investigator"),
    SENIOR_INVESTIGATOR("Senior Investigator"),
    MASTER_DETECTIVE("Master Detective"); // Example of adding another rank easily

    private final String displayName;

    Rank(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the user-friendly display name for this rank.
     * @return The display name string.
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName; // So System.out.println(rankObject) prints the nice name.
    }
}