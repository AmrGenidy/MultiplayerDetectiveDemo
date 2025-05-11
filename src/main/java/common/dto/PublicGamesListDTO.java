package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PublicGamesListDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final List<PublicGameInfoDTO> games;

  public PublicGamesListDTO(List<PublicGameInfoDTO> games) {
    this.games = games != null ? new ArrayList<>(games) : new ArrayList<>();
  }

  public List<PublicGameInfoDTO> getGames() {
    return new ArrayList<>(games);
  }

  @Override
  public String toString() {
    return "PublicGamesListDTO{" + "games_count=" + games.size() + '}';
  }
}
