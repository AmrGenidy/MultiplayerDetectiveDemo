package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomDescriptionDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String name;
  private final String description;
  private final List<String> objectNames;
  private final List<String> occupantNames; // Includes other players and NPCs
  private final Map<String, String> exits; // Direction -> RoomName

  public RoomDescriptionDTO(
      String name,
      String description,
      List<String> objectNames,
      List<String> occupantNames,
      Map<String, String> exits) {
    this.name = name;
    this.description = description;
    this.objectNames = objectNames != null ? new ArrayList<>(objectNames) : new ArrayList<>();
    this.occupantNames = occupantNames != null ? new ArrayList<>(occupantNames) : new ArrayList<>();
    this.exits = exits != null ? new HashMap<>(exits) : new HashMap<>();
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public List<String> getObjectNames() {
    return new ArrayList<>(objectNames);
  }

  public List<String> getOccupantNames() {
    return new ArrayList<>(occupantNames);
  }

  public Map<String, String> getExits() {
    return new HashMap<>(exits);
  }

  @Override
  public String toString() {
    // A more detailed toString for debugging or simple client display
    StringBuilder sb = new StringBuilder();
    sb.append("Room: ").append(name).append("\n");
    sb.append(description).append("\n");
    sb.append("Objects: ")
        .append(objectNames.isEmpty() ? "None" : String.join(", ", objectNames))
        .append("\n");
    sb.append("Occupants: ")
        .append(occupantNames.isEmpty() ? "None" : String.join(", ", occupantNames))
        .append("\n");
    sb.append("Exits: ");
    if (exits.isEmpty()) {
      sb.append("None");
    } else {
      exits.forEach((dir, room) -> sb.append(dir).append(" (").append(room).append("), "));
      if (!exits.isEmpty()) sb.setLength(sb.length() - 2); // remove last comma and space
    }
    return sb.toString();
  }
}
