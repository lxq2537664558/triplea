package org.triplea.lobby.server;

import java.util.logging.Level;

import org.triplea.lobby.server.config.LobbyPropertyReader;

import games.strategy.engine.config.FilePropertyReader;
import games.strategy.util.ExitStatus;
import lombok.extern.java.Log;

/**
 * Runs a lobby server.
 */
@Log
public final class LobbyRunner {
  private LobbyRunner() {}

  /**
   * Entry point for running a new lobby server. The lobby server runs until the process is killed or the lobby server
   * is shut down via administrative command.
   */
  public static void main(final String[] args) {
    try {
      final LobbyPropertyReader lobbyPropertyReader =
          new LobbyPropertyReader(new FilePropertyReader("config/lobby/lobby.properties"));
      log.info("Starting lobby on port " + lobbyPropertyReader.getPort());
      LobbyServer.start(lobbyPropertyReader);
      log.info("Lobby started");
    } catch (final Exception e) {
      log.log(Level.SEVERE, "Failed to start lobby", e);
      ExitStatus.FAILURE.exit();
    }
  }
}
