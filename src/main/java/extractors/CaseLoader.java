package extractors; // Move to server package structure

import JsonDTO.CaseFile; // Keep using the JSON DTO
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature; // Optional: For more robust parsing

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for loading CaseFile definitions from JSON files
 * within a specified directory.
 * Intended for server-side use.
 */
public class CaseLoader {

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // Be lenient with extra fields

    private CaseLoader() {} // Prevent instantiation

    /**
     * Loads all valid CaseFile definitions found as .json files in the specified directory.
     *
     * @param directoryPath The path to the directory containing case JSON files.
     * @return A List of loaded CaseFile objects. Returns an empty list if the directory
     *         doesn't exist, isn't a directory, or no valid case files are found.
     */
    public static List<CaseFile> loadCases(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Case directory not found or is not a directory: " + directoryPath);
            return Collections.emptyList();
        }

        List<CaseFile> cases = new ArrayList<>();
        //System.out.println("Loading cases from directory: " + dir.toAbsolutePath());

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                    .forEach(filePath -> {
                        File file = filePath.toFile();
                        try {
                            // System.out.println("Attempting to load case: " + file.getName()); // Debug
                            CaseFile caseFile = mapper.readValue(file, CaseFile.class);
                            // Basic validation after loading
                            if (caseFile.getTitle() != null && !caseFile.getTitle().isBlank() &&
                                    caseFile.getRooms() != null && !caseFile.getRooms().isEmpty()) {
                                // Optionally set the file path in the CaseFile object if needed later?
                                // caseFile.setFilePath(filePath.toString()); // Requires setter in CaseFile
                                cases.add(caseFile);
                                //System.out.println("Successfully loaded case: " + caseFile.getTitle() + " from " + file.getName());
                            } else {
                                System.err.println("Error loading case from " + file.getName() + ": Invalid structure (missing title or rooms). Skipping.");
                            }
                        } catch (IOException e) {
                            System.err.println("Error reading or parsing case file " + file.getName() + ": " + e.getMessage());
                            // e.printStackTrace(); // Optionally print stack trace for detailed debugging
                        } catch (Exception e) { // Catch other potential runtime exceptions during load
                            System.err.println("Unexpected error loading case file " + file.getName() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error listing files in case directory " + directoryPath + ": " + e.getMessage());
            return Collections.emptyList(); // Return empty on directory error
        }

        System.out.println("Finished loading cases. Found " + cases.size() + " valid case(s).");
        return cases;
    }
}