package singleplayer.util; // Or wherever you place it

import JsonDTO.CaseFile; // Make sure this import is correct for your CaseFile DTO
import com.fasterxml.jackson.databind.ObjectMapper; // If you're using Jackson
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption; // For potentially overwriting if needed, or specific copy options
import java.util.ArrayList;
import java.util.List;
import singleplayer.SinglePlayerMain;

public class CaseFileUtil {

    private CaseFileUtil() {} // Utility class

    // This is your original addCaseFromFile method
    public static void addCaseFromFile(String filePath) {
        ObjectMapper mapper = new ObjectMapper(); // Or your preferred JSON library
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("Error: Source file does not exist: " + filePath);
                return;
            }
            if (!file.isFile()) {
                System.out.println("Error: Source path is not a file: " + filePath);
                return;
            }
            if (!filePath.toLowerCase().endsWith(".json")) {
                System.out.println("Error: File must be a .json case file.");
                return;
            }


            CaseFile newCase = mapper.readValue(file, CaseFile.class);
            if (newCase.getTitle() == null || newCase.getTitle().trim().isEmpty()) {
                System.out.println("Error: The case file is missing a title or the title is empty.");
                return;
            }


            // Check for duplicates based on case title in the target 'cases' directory
            List<CaseFile> existingCases = loadExistingCasesFromCasesDir(); // Use a specific method for target dir
            for (CaseFile caseFile : existingCases) {
                if (caseFile.getTitle().equalsIgnoreCase(newCase.getTitle())) {
                    System.out.println("Error: A case with the title '" + newCase.getTitle() + "' already exists in the 'cases' directory.");
                    return;
                }
            }

            File casesFolder = new File(SinglePlayerMain.CASES_DIRECTORY); // Use constant from SinglePlayerMain
            if (!casesFolder.exists()) {
                if (!casesFolder.mkdirs()) { // Use mkdirs for creating parent dirs if needed
                    System.out.println("Error: Could not create the 'cases' directory.");
                    return;
                }
            }
            if (!casesFolder.isDirectory()) {
                System.out.println("Error: The path '" + casesFolder.getPath() + "' exists but is not a directory.");
                return;
            }


            // Use the original file name for the destination first
            String originalFileName = file.getName();
            File destination = new File(casesFolder, originalFileName);

            // Handle if a file with the same name already exists (e.g., title is different but filename clashes)
            int counter = 1;
            String fileNameWithoutExtension = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
            String extension = originalFileName.substring(originalFileName.lastIndexOf('.'));

            while (destination.exists()) {
                // If it exists, and if titles match, we already returned.
                // If titles DON'T match, but filename clashes, generate new name.
                // (This part of logic might need refinement if titles can be different but filenames same for DIFFERENT cases)
                // For simplicity, this loop assumes if filename exists, we try a new one.
                // A better check would be to load the existing file and compare titles IF a file with same name exists.
                // But since we check titles first, this loop primarily handles exact filename clashes for different cases.
                String newFileName = fileNameWithoutExtension + "_" + counter + extension;
                destination = new File(casesFolder, newFileName);
                counter++;
            }

            // Copy the file to the destination
            Files.copy(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING); // Or other options

            System.out.println("Success! Case '" + newCase.getTitle() + "' added to 'cases' directory.");
            System.out.println("File saved as: " + destination.getName());

        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            System.out.println("Error: Could not parse the JSON case file. Ensure it is valid JSON.");
            e.printStackTrace();
        } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
            System.out.println("Error: Could not map JSON to CaseFile object. Check JSON structure and field names.");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Error during file operation: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) { // Catch-all for other unexpected issues
            System.out.println("An unexpected error occurred while adding the case: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // This is your original loadExistingCases method, slightly renamed for clarity
    private static List<CaseFile> loadExistingCasesFromCasesDir() {
        ObjectMapper mapper = new ObjectMapper();
        File folder = new File(SinglePlayerMain.CASES_DIRECTORY); // Use constant
        List<CaseFile> cases = new ArrayList<>();

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try {
                        CaseFile caseFile = mapper.readValue(file, CaseFile.class);
                        if (caseFile.getTitle() != null && !caseFile.getTitle().trim().isEmpty()) {
                            cases.add(caseFile);
                        } else {
                            System.out.println("Warning: Skipping case file with no title: " + file.getName());
                        }
                    } catch (Exception e) {
                        System.out.println("Error loading existing case file: " + file.getName() + " - " + e.getMessage());
                        // e.printStackTrace(); // Optionally show full trace
                    }
                }
            }
        }
        return cases;
    }
}