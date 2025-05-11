package Core; // Assuming 'core' is the package name

import java.util.HashMap;
import java.util.Map;
import java.io.Serializable; // Added for potential serialization if Room objects are ever directly sent/saved

public class Room implements Serializable { // Made concrete and Serializable
  private static final long serialVersionUID = 1L; // For Serializable

  // Changed to private, accessible via getters.
  // 'name' can be final if set only at construction and never changed.
  private final String name;
  private String description;
  protected Map<String, Room> neighbors = new HashMap<>();
  protected Map<String, GameObject> objects = new HashMap<>();

  public Room(String name, String description) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Room name cannot be null or empty.");
    }
    this.name = name;
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) { // Added setter if description can change
    this.description = description;
  }

  public Map<String, Room> getNeighbors() {
    return new HashMap<>(neighbors); // Return a copy to prevent external modification
  }

  public void setNeighbor(String direction, Room neighbor) {
    if (direction == null || direction.trim().isEmpty() || neighbor == null) {
      // Or throw IllegalArgumentException
      System.err.println("Invalid direction or neighbor for setNeighbor.");
      return;
    }
    neighbors.put(direction.toLowerCase(), neighbor);
  }

  public Room getNeighbor(String direction) {
    if (direction == null) return null;
    return neighbors.get(direction.toLowerCase());
  }

  public GameObject getObject(String objectName) {
    if (objectName == null) return null;
    return objects.get(objectName.toLowerCase());
  }

  public void addObject(GameObject object) { // Changed to take GameObject directly
    if (object == null || object.getName() == null || object.getName().trim().isEmpty()) {
      // Or throw IllegalArgumentException
      System.err.println("Invalid object or object name for addObject.");
      return;
    }
    objects.put(object.getName().toLowerCase(), object);
  }

  public Map<String, GameObject> getObjects() {
    return new HashMap<>(objects); // Return a copy
  }

  public String getExitsDescription() {
    if (neighbors.isEmpty()) {
      return "Exits: None";
    }
    StringBuilder sb = new StringBuilder("Exits: ");
    // Using stream for a slightly more modern approach, but for-each is fine too
    neighbors.forEach((direction, room) -> sb.append(direction).append(" (").append(room.getName()).append("), "));
    if (sb.length() > "Exits: ".length()) {
      sb.setLength(sb.length() - 2); // Remove trailing comma and space
    }
    return sb.toString();
  }

  public String getObjectsDescription() {
    if (objects.isEmpty()) {
      return "Objects present: None"; // Changed for consistency
    }
    StringBuilder sb = new StringBuilder("Objects present: ");
    objects.values().forEach(obj -> sb.append(obj.getName()).append(", "));
    if (sb.length() > "Objects present: ".length()) {
      sb.setLength(sb.length() - 2); // Remove trailing comma and space
    }
    return sb.toString();
  }

  /**
   * Examines an object in the room.
   * Note: This logic might be better handled entirely within an ExamineCommand
   * to keep Room focused on state. For now, it provides basic functionality.
   * The command would call room.getObject(objectName) and then obj.getExamine().
   *
   * @param objectName The name of the object to examine.
   * @return A string describing the examination, or a message if the object isn't found.
   */
  public String examineObject(String objectName) {
    GameObject obj = getObject(objectName);
    if (obj != null) {
      String examineText = obj.getExamine();
      if (examineText != null && !examineText.isEmpty()) {
        return "You examine the " + obj.getName() + ": " + examineText;
      }
      // Fallback to description if no specific examine text
      return "You examine the " + obj.getName() + ": " + obj.getDescription();
    }
    return "There is no '" + objectName + "' to examine here.";
  }

  // Override equals and hashCode if rooms are stored in Sets or as Map keys
  // based on their name, for example.
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Room room = (Room) o;
    return name.equals(room.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "Room{" +
            "name='" + name + '\'' +
            ", description='" + description + '\'' +
            '}';
  }
}