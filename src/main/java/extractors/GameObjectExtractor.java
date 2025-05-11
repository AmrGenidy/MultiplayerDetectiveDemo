package extractors; // Ensure this package matches your directory structure

import common.interfaces.GameContext;
import Core.GameObject; // Assuming GameObject is in 'core' package
import Core.Room;     // Assuming Room is in 'core' package
import JsonDTO.CaseFile;
import java.util.Objects;

public class GameObjectExtractor {

    private GameObjectExtractor() {} // Private constructor for utility class

    public static void loadObjects(CaseFile caseFile, GameContext context) {
        Objects.requireNonNull(caseFile, "CaseFile cannot be null for GameObjectExtractor.");
        Objects.requireNonNull(context, "GameContext cannot be null for GameObjectExtractor.");
        String contextId = context.getContextIdForLog() != null ? context.getContextIdForLog() : "UnknownContext";

        context.logLoadingMessage("Starting to load game objects..."); // Use context logger
        int objectsLoaded = 0;
        int roomNotFoundErrors = 0;
        int invalidObjectDataErrors = 0;

        if (caseFile.getRooms() == null) {
            context.logLoadingMessage("Error: CaseFile contains no rooms data. Cannot load objects.");
            return;
        }

        for (CaseFile.RoomData roomData : caseFile.getRooms()) {
            if (roomData == null || roomData.getName() == null || roomData.getName().trim().isEmpty()) {
                context.logLoadingMessage("Warning: Skipping a room with null or empty name in CaseFile during object loading.");
                continue;
            }

            Room room = context.getRoomByName(roomData.getName());

            if (room != null) {
                if (roomData.getObjects() != null) {
                    for (CaseFile.GameObjectData objData : roomData.getObjects()) {
                        if (objData == null) {
                            context.logLoadingMessage("Warning: Found null GameObjectData in room '" + roomData.getName() + "'. Skipping.");
                            invalidObjectDataErrors++;
                            continue;
                        }
                        if (objData.getName() == null || objData.getName().trim().isEmpty()) {
                            context.logLoadingMessage("Warning: GameObject in room '" + roomData.getName() + "' has no name. Skipping.");
                            invalidObjectDataErrors++;
                            continue;
                        }
                        // Providing default values if JSON fields are missing
                        String description = (objData.getDescription() != null && !objData.getDescription().trim().isEmpty())
                                ? objData.getDescription()
                                : "A nondescript " + objData.getName() + ".";
                        String examineText = (objData.getExamine() != null && !objData.getExamine().trim().isEmpty())
                                ? objData.getExamine()
                                : description; // Default examine uses (potentially defaulted) description
                        String deduceText = (objData.getDeduce() != null && !objData.getDeduce().trim().isEmpty())
                                ? objData.getDeduce()
                                : "You find nothing particularly revealing to deduce about the " + objData.getName() + ".";

                        GameObject obj = new GameObject(
                                objData.getName().trim(), // Ensure name is trimmed
                                description,
                                examineText,
                                deduceText
                        );

                        // --- FIX APPLIED HERE ---
                        room.addObject(obj);
                        // --- End FIX ---

                        objectsLoaded++;
                    }
                }
            } else {
                context.logLoadingMessage("Warning: Room '" + roomData.getName() + "' defined in CaseFile's object section not found in context. Objects for this room cannot be loaded.");
                roomNotFoundErrors++;
            }
        }
        context.logLoadingMessage("Finished loading game objects. Total Loaded: " + objectsLoaded +
                ", Invalid Object Data Errors: " + invalidObjectDataErrors +
                ", Room Not Found Errors: " + roomNotFoundErrors);
    }
}