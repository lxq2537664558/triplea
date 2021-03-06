package games.strategy.engine.random;

import java.util.ArrayList;
import java.util.List;

import games.strategy.engine.framework.IGame;
import games.strategy.engine.framework.VerifiedRandomNumbers;
import games.strategy.engine.vault.NotUnlockedException;
import games.strategy.engine.vault.Vault;
import games.strategy.engine.vault.VaultID;

/**
 * Default implementation of {@link IRemoteRandom}.
 */
public class RemoteRandom implements IRemoteRandom {
  private static final List<VerifiedRandomNumbers> verifiedRandomNumbers = new ArrayList<>();

  public static synchronized List<VerifiedRandomNumbers> getVerifiedRandomNumbers() {
    return new ArrayList<>(verifiedRandomNumbers);
  }

  private static synchronized void addVerifiedRandomNumber(final VerifiedRandomNumbers number) {
    verifiedRandomNumbers.add(number);
  }

  private final PlainRandomSource plainRandom = new PlainRandomSource();
  private final IGame game;
  // remembered from generate to unlock
  private VaultID remoteVaultId;
  private String annotation;
  private int max;
  // have we recieved a generate request, but not a unlock request
  private boolean waitingForUnlock;
  private int[] localNumbers;

  /**
   * Creates a new instance of RemoteRandom.
   */
  public RemoteRandom(final IGame game) {
    this.game = game;
  }

  @Override
  public int[] generate(final int max, final int count, final String annotation, final VaultID remoteVaultId)
      throws IllegalStateException {
    if (waitingForUnlock) {
      throw new IllegalStateException("Being asked to generate random numbers, but we havent finished last generation. "
          // TODO: maybe we should wait instead of crashing the game?
          + "Asked for: " + count + "x" + max + " for " + annotation);
    }
    waitingForUnlock = true;
    // clean up here, we know these keys arent needed anymore so release them
    // we cant do this earlier without synchronizing between the server and the client
    // but here we know they arent needed anymore
    if (this.remoteVaultId != null) {
      game.getVault().release(this.remoteVaultId);
    }
    this.remoteVaultId = remoteVaultId;
    this.annotation = annotation;
    this.max = max;
    localNumbers = plainRandom.getRandom(max, count, annotation);
    game.getVault().waitForId(remoteVaultId, 15000);
    if (!game.getVault().knowsAbout(remoteVaultId)) {
      throw new IllegalStateException(
          "Vault id not known, have:" + game.getVault().knownIds() + " looking for:" + remoteVaultId);
    }
    return localNumbers;
  }

  @Override
  public void verifyNumbers() throws IllegalStateException {
    final Vault vault = game.getVault();
    vault.waitForIdToUnlock(remoteVaultId, 15000);
    if (!vault.isUnlocked(remoteVaultId)) {
      throw new IllegalStateException("Server did not unlock random numbers, cheating is suspected");
    }
    final int[] remoteNumbers;
    try {
      remoteNumbers = CryptoRandomSource.bytesToInts(vault.get(remoteVaultId));
    } catch (final NotUnlockedException e1) {
      throw new IllegalStateException("Could not unlock numbers, cheating suspected", e1);
    }
    final int[] verifiedNumbers = CryptoRandomSource.mix(remoteNumbers, localNumbers, max);
    addVerifiedRandomNumber(new VerifiedRandomNumbers(annotation, verifiedNumbers));
    waitingForUnlock = false;
  }
}
