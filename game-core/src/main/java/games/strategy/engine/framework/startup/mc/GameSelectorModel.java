package games.strategy.engine.framework.startup.mc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Observable;
import java.util.logging.Level;

import com.google.common.base.Preconditions;

import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameParseException;
import games.strategy.engine.data.GameParser;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.ui.GameChooserEntry;
import games.strategy.engine.framework.ui.GameChooserModel;
import games.strategy.triplea.ai.pro.ProAi;
import games.strategy.triplea.settings.ClientSetting;
import lombok.extern.java.Log;

/**
 * Model class that tracks the currently 'selected' game. This is the info that appears in the
 * game selector panel on the staging screens, eg: map, round, filename.
 */
@Log
public class GameSelectorModel extends Observable {
  private GameData gameData = null;
  private String gameName = "";
  private String gameVersion = "";
  private String gameRound = "";
  private String fileName;
  private boolean canSelect = true;
  private boolean isHostHeadlessBot = false;
  // just for host bots, so we can get the actions for loading/saving games on the bots
  // from this model
  private ClientModel clientModelForHostBots = null;

  public GameSelectorModel() {
    resetGameDataToNull();
  }

  public void resetGameDataToNull() {
    setGameData(null);
    fileName = "";
  }

  public void load(final GameData data, final String fileName) {
    setGameData(data);
    this.fileName = fileName;
  }

  public void load(final GameChooserEntry entry) {
    fileName = entry.getLocation();
    setGameData(entry.getGameData());
    if (entry.getGameData() != null) {
      ClientSetting.DEFAULT_GAME_NAME_PREF.save(entry.getGameData().getGameName());
    }
    ClientSetting.DEFAULT_GAME_URI_PREF.save(entry.getUri().toString());
    ClientSetting.flush();
  }

  /**
   * Loads game data by parsing a given file.
   */
  public void load(final File file) {
    Preconditions.checkArgument(file.exists() && file.isFile(),
        "File should exist at: " + file.getAbsolutePath());

    final GameData newData;
    try {
      // if the file name is xml, load it as a new game
      if (file.getName().toLowerCase().endsWith("xml")) {
        try (InputStream inputStream = new FileInputStream(file)) {
          newData = GameParser.parse(file.getAbsolutePath(), inputStream);
        }
      } else {
        // try to load it as a saved game whatever the extension
        newData = GameDataManager.loadGame(file);
      }
      if (newData != null) {
        fileName = file.getName();
        setGameData(newData);
      }
    } catch (final Exception e) {
      log.log(Level.SEVERE,
          String.format("Error loading game file: %s, %s",
              file.getAbsolutePath(), e.getMessage()),
          e);
    }
  }

  public GameData getGameData(final InputStream input) {
    try {
      final GameData newData = GameDataManager.loadGame(input);
      if (newData != null) {
        return newData;
      }
    } catch (final IOException e) {
      log.log(Level.SEVERE, "Failed to load game", e);
    }
    return null;
  }

  public synchronized GameData getGameData() {
    return gameData;
  }

  public synchronized boolean isSavedGame() {
    return !fileName.endsWith(".xml");
  }

  void setCanSelect(final boolean canSelect) {
    synchronized (this) {
      this.canSelect = canSelect;
    }
    notifyObs();
  }

  public synchronized boolean canSelect() {
    return canSelect;
  }

  void setIsHostHeadlessBot(final boolean isHostHeadlessBot) {
    synchronized (this) {
      this.isHostHeadlessBot = isHostHeadlessBot;
    }
    notifyObs();
  }

  public synchronized boolean isHostHeadlessBot() {
    return isHostHeadlessBot;
  }

  void setClientModelForHostBots(final ClientModel clientModel) {
    synchronized (this) {
      clientModelForHostBots = clientModel;
    }
  }

  public synchronized ClientModel getClientModelForHostBots() {
    return clientModelForHostBots;
  }

  /**
   * We dont have a gane data (ie we are a remote player and the data has not been sent yet), but
   * we still want to display game info.
   */
  public void clearDataButKeepGameInfo(final String gameName, final String gameRound, final String gameVersion) {
    synchronized (this) {
      gameData = null;
      this.gameName = gameName;
      this.gameRound = gameRound;
      this.gameVersion = gameVersion;
    }
    notifyObs();
  }

  public synchronized String getFileName() {
    return (gameData == null) ? "-" : fileName;
  }

  public synchronized String getGameName() {
    return gameName;
  }

  public synchronized String getGameRound() {
    return gameRound;
  }

  public synchronized String getGameVersion() {
    return gameVersion;
  }

  void setGameData(final GameData data) {
    synchronized (this) {
      if (data == null) {
        gameName = gameRound = gameVersion = "-";
      } else {
        gameName = data.getGameName();
        gameRound = "" + data.getSequence().getRound();
        gameVersion = data.getGameVersion().toString();
      }
      gameData = data;
    }
    notifyObs();
  }

  private void notifyObs() {
    super.setChanged();
    super.notifyObservers(gameData);
    super.clearChanged();
  }

  /**
   * Clears AI game over cache and loads default game in a new thread.
   */
  public void loadDefaultGameNewThread() {
    // clear out ai cached properties (this ended up being the best place to put it, as we have definitely left a game
    // at this point)
    ProAi.gameOverClearCache();
    new Thread(this::loadDefaultGameSameThread).start();
  }

  /**
   * Runs the load default game logic in same thread. Default game is the one that we loaded
   * on startup.
   */
  public void loadDefaultGameSameThread() {
    final String userPreferredDefaultGameUri = ClientSetting.DEFAULT_GAME_URI_PREF.value();

    // we don't want to load a game file by default that is not within the map folders we can load. (ie: if a previous
    // version of triplea
    // was using running a game within its root folder, we shouldn't open it)
    GameChooserEntry selectedGame;
    final String user = ClientFileSystemHelper.getUserRootFolder().toURI().toString();
    if (!userPreferredDefaultGameUri.isEmpty() && userPreferredDefaultGameUri.contains(user)) {
      // if the user has a preferred URI, then we load it, and don't bother parsing or doing anything with the whole
      // game model list
      try {
        final URI defaultUri = new URI(userPreferredDefaultGameUri);
        selectedGame = new GameChooserEntry(defaultUri);
      } catch (final Exception e) {
        resetToFactoryDefault();
        selectedGame = selectByName();
        if (selectedGame == null) {
          return;
        }
      }
      if (!selectedGame.isGameDataLoaded()) {
        try {
          selectedGame.fullyParseGameData();
        } catch (final GameParseException e) {
          resetToFactoryDefault();
          loadDefaultGameSameThread();
          return;
        }
      }
    } else {
      resetToFactoryDefault();
      selectedGame = selectByName();
      if (selectedGame == null) {
        return;
      }
    }
    load(selectedGame);
  }

  private static void resetToFactoryDefault() {
    ClientSetting.DEFAULT_GAME_URI_PREF.save(ClientSetting.DEFAULT_GAME_URI_PREF.defaultValue);
    ClientSetting.flush();
  }

  private static GameChooserEntry selectByName() {
    final String userPreferredDefaultGameName = ClientSetting.DEFAULT_GAME_NAME_PREF.value();

    final GameChooserModel model = new GameChooserModel();
    GameChooserEntry selectedGame = model.findByName(userPreferredDefaultGameName)
        .orElse(null);

    if (selectedGame == null && model.size() > 0) {
      selectedGame = model.get(0);
    }
    if (selectedGame == null) {
      return null;
    }
    if (!selectedGame.isGameDataLoaded()) {
      try {
        selectedGame.fullyParseGameData();
      } catch (final GameParseException e) {
        model.removeEntry(selectedGame);
        resetToFactoryDefault();
        return null;
      }
    }
    return selectedGame;
  }

  /**
   * Formats the file name text to two lines.
   * The separation focuses on the second line being at least the filename while the first line
   * should show the the path including '...' in case it does not fit
   *
   * @return filename formatted file name - in case it is too long (> maxLength) to two lines,
   */
  public String getFormattedFileNameText() {
    final int maxLength = Math.max(22, 3 + gameName.length() + gameName.length());
    if (fileName.length() <= maxLength) {
      return fileName;
    }
    int cutoff = fileName.length() - maxLength;
    String secondLine = fileName.substring(cutoff);
    if (secondLine.contains("/")) {
      cutoff += secondLine.indexOf("/") + 1;
    }
    secondLine = fileName.substring(cutoff);
    String firstLine = fileName.substring(0, cutoff);
    if (firstLine.length() > maxLength) {
      firstLine = firstLine.substring(0, maxLength - 4);
      if (firstLine.contains("/")) {
        cutoff = firstLine.lastIndexOf("/") + 1;
        firstLine = firstLine.substring(0, cutoff) + ".../";
      } else {
        firstLine = firstLine + "...";
      }
    }
    return "<html><p>" + firstLine + "<br/>" + secondLine + "</p></html>";
  }
}
