package singleplayer.util; // Assuming it's in this package, adjust if needed.

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import singleplayer.SinglePlayerMain;

/**
 * CaseFileUtil Utility class for operations related to case files, like adding new ones from the
 * local file system to the game's 'cases' directory. Primarily for single-player or admin-tool use.
 */
public class CaseFileUtil {

  // Utility class, so no instances needed.
  private CaseFileUtil() {}

  /**
   * Adds a new case from a user-specified JSON file into the game's 'cases' directory. It checks
   * for file existence, valid JSON, and duplicate case titles. Handles filename clashes by
   * appending a counter.
   *
   * @param filePath The absolute or relative path to the .json case file to be added.
   */
  public static void addCaseFromFile(String filePath) {
    ObjectMapper mapper = new ObjectMapper(); // My JSON parser/mapper.

    try {
      // --- Input File Validation ---
      File sourceFile = new File(filePath);
      if (!sourceFile.exists()) {
        System.out.println("ADD_CASE_ERROR: Source file not found: " + filePath);
        return;
      }
      if (!sourceFile.isFile()) {
        System.out.println("ADD_CASE_ERROR: Source path is not a regular file: " + filePath);
        return;
      }
      if (!filePath.toLowerCase().endsWith(".json")) {
        System.out.println("ADD_CASE_ERROR: Source file must be a .json file.");
        return;
      }

      // --- Read and Validate New Case Content ---
      CaseFile newCaseContent;
      try {
        newCaseContent = mapper.readValue(sourceFile, CaseFile.class);
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) { // More specific catch
        System.out.println(
            "ADD_CASE_ERROR: Could not parse the JSON in '"
                + sourceFile.getName()
                + "'. Ensure it's valid JSON.");
        // e.printStackTrace(); // Good for dev, maybe not for user.
        return;
      }

      if (newCaseContent.getTitle() == null || newCaseContent.getTitle().trim().isEmpty()) {
        System.out.println(
            "ADD_CASE_ERROR: The case file '"
                + sourceFile.getName()
                + "' is missing a valid title.");
        return;
      }

      // --- Check for Duplicate Titles in Target Directory ---
      List<CaseFile> existingCases = loadExistingCasesFromCasesDir();
      for (CaseFile existingCase : existingCases) {
        if (existingCase.getTitle().equalsIgnoreCase(newCaseContent.getTitle())) {
          System.out.println(
              "ADD_CASE_ERROR: A case titled '"
                  + newCaseContent.getTitle()
                  + "' already exists in the '"
                  + SinglePlayerMain.CASES_DIRECTORY
                  + "' directory.");
          return;
        }
      }

      // --- Prepare Target Directory ---
      File targetCasesFolder = new File(SinglePlayerMain.CASES_DIRECTORY);
      if (!targetCasesFolder.exists()) {
        if (!targetCasesFolder.mkdirs()) { // mkdirs() for parent directories too.
          System.out.println(
              "ADD_CASE_ERROR: Could not create target 'cases' directory at: "
                  + targetCasesFolder.getAbsolutePath());
          return;
        }
        System.out.println(
            "ADD_CASE_INFO: Created 'cases' directory: " + targetCasesFolder.getAbsolutePath());
      }
      if (!targetCasesFolder.isDirectory()) {
        System.out.println(
            "ADD_CASE_ERROR: Target path '"
                + targetCasesFolder.getAbsolutePath()
                + "' is not a directory.");
        return;
      }

      // --- Determine Destination Filename (handle clashes) ---
      String originalFileName = sourceFile.getName();
      File destinationFile = new File(targetCasesFolder, originalFileName);
      int counter = 1;
      // Just in case of filename clash for *different* cases (title check already done).
      // Or if I want to allow overwriting based on a flag later.
      while (destinationFile.exists()) {
        // This loop ensures a unique filename if one with the same name already exists.
        // Since titles are unique, this is mostly for rare filename clashes where titles differ.
        String namePart = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String extPart = originalFileName.substring(originalFileName.lastIndexOf('.'));
        String newFileName = namePart + "_" + counter + extPart;
        destinationFile = new File(targetCasesFolder, newFileName);
        counter++;
      }

      // --- Copy the File ---
      Files.copy(
          sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      // Using REPLACE_EXISTING because the while loop should ensure destinationFile is a new name
      // if needed.
      // If I didn't have the while loop, I might use COPY_ATTRIBUTES or THROW_IF_EXISTS.

      System.out.println("ADD_CASE_SUCCESS: Case '" + newCaseContent.getTitle() + "' added.");
      System.out.println(
          "                  Saved as: "
              + destinationFile.getName()
              + " in '"
              + targetCasesFolder.getName()
              + "' directory.");

    } catch (IOException e) { // Catch IO errors from file operations.
      System.out.println(
          "ADD_CASE_IO_ERROR: An error occurred during file operation: " + e.getMessage());
      // e.printStackTrace(); // Useful for debugging.
    } catch (Exception e) { // Catch-all for any other unexpected issues.
      System.out.println(
          "ADD_CASE_UNEXPECTED_ERROR: An unexpected error occurred: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Loads all valid case files from the game's 'cases' directory. Used to check for duplicate
   * titles before adding a new case.
   *
   * @return A List of CaseFile objects.
   */
  private static List<CaseFile> loadExistingCasesFromCasesDir() {
    ObjectMapper mapper = new ObjectMapper();
    File folder = new File(SinglePlayerMain.CASES_DIRECTORY); // Use the constant for consistency.
    List<CaseFile> cases = new ArrayList<>();

    if (folder.exists() && folder.isDirectory()) {
      // Filter for .json files only.
      File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
      if (files != null) {
        for (File file : files) {
          try {
            CaseFile caseFile = mapper.readValue(file, CaseFile.class);
            // Important: Only add if it has a valid title, otherwise it can't be identified.
            if (caseFile.getTitle() != null && !caseFile.getTitle().trim().isEmpty()) {
              cases.add(caseFile);
            } else {
              System.out.println(
                  "LOAD_CASES_WARN: Skipping case file '" + file.getName() + "' (missing title).");
            }
          } catch (Exception e) { // Catch broad exception during loading of existing files.
            // Don't want one bad file to stop the whole "add case" process if possible.
            System.out.println(
                "LOAD_CASES_ERROR: Error parsing existing case file '"
                    + file.getName()
                    + "': "
                    + e.getMessage());
          }
        }
      }
    }
    return cases;
  }
}
