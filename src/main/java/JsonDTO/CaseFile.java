package JsonDTO;

import java.util.List;
import java.util.Map;

public class CaseFile {
  private String title;
  private String invitation;
  private String description;
  private String startingRoom;
  private List<SuspectData> suspects;
  private List<RoomData> rooms;
  private List<ExamQuestion> finalExam;
  private List<String> tasks;
  private List<String> watsonHints;

  public List<String> getWatsonHints() {
    return watsonHints;
  }

  // Nested classes for JSON data
  public static class SuspectData {
    private String name;
    private String statement;
    private String clue;
    private List<String> allowedRooms;

    // Public getters
    public String getName() {
      return name;
    }

    public String getStatement() {
      return statement;
    }

    public String getClue() {
      return clue;
    }

    public List<String> getAllowedRooms() {
      return allowedRooms;
    }
  }

  public static class RoomData {
    private String name;
    private String description;
    private Map<String, String> neighbors;
    private List<GameObjectData> objects;

    // Getters
    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public Map<String, String> getNeighbors() {
      return neighbors;
    }

    public List<GameObjectData> getObjects() {
      return objects;
    }
  }

  public static class GameObjectData {
    private String name;
    private String description;
    private String deduce;
    private String examine;

    // Getters
    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public String getDeduce() {
      return deduce;
    }

    public String getExamine() {
      return examine;
    }
  }

  public static class ExamQuestion {
    private String question;
    private String answer;

    // Getters
    public String getQuestion() {
      return question;
    }

    public String getAnswer() {
      return answer;
    }
  }

  // Getters for CaseFile
  public String getTitle() {
    return title;
  }

  public String getInvitation() {
    return invitation;
  }

  public String getDescription() {
    return description;
  }

  public String getStartingRoom() {
    return startingRoom;
  }

  public List<SuspectData> getSuspects() {
    return suspects;
  }

  public List<RoomData> getRooms() {
    return rooms;
  }

  public List<ExamQuestion> getFinalExam() {
    return finalExam;
  }

  public List<String> getTasks() {
    return tasks;
  }
}
