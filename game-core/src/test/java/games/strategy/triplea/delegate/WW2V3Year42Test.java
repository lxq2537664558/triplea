package games.strategy.triplea.delegate;

import static games.strategy.triplea.delegate.GameDataTestUtil.addTo;
import static games.strategy.triplea.delegate.GameDataTestUtil.battleDelegate;
import static games.strategy.triplea.delegate.GameDataTestUtil.battleship;
import static games.strategy.triplea.delegate.GameDataTestUtil.carrier;
import static games.strategy.triplea.delegate.GameDataTestUtil.fighter;
import static games.strategy.triplea.delegate.GameDataTestUtil.germans;
import static games.strategy.triplea.delegate.GameDataTestUtil.italians;
import static games.strategy.triplea.delegate.GameDataTestUtil.move;
import static games.strategy.triplea.delegate.GameDataTestUtil.moveDelegate;
import static games.strategy.triplea.delegate.GameDataTestUtil.removeFrom;
import static games.strategy.triplea.delegate.GameDataTestUtil.russians;
import static games.strategy.triplea.delegate.GameDataTestUtil.territory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.xml.TestMapGameData;

public class WW2V3Year42Test {
  private GameData gameData;

  @BeforeEach
  public void setUp() throws Exception {
    gameData = TestMapGameData.WW2V3_1942.getGameData();
  }

  private ITestDelegateBridge getDelegateBridge(final PlayerID player) {
    return GameDataTestUtil.getDelegateBridge(player, gameData);
  }

  @Test
  public void testTransportAttack() {
    final Territory sz13 = gameData.getMap().getTerritory("13 Sea Zone");
    final Territory sz12 = gameData.getMap().getTerritory("12 Sea Zone");
    final PlayerID germans = germans(gameData);
    final MoveDelegate moveDelegate = (MoveDelegate) gameData.getDelegateList().getDelegate("move");
    final ITestDelegateBridge bridge = getDelegateBridge(germans);
    bridge.setStepName("CombatMove");
    moveDelegate(gameData).setDelegateBridgeAndPlayer(bridge);
    moveDelegate(gameData).start();
    final Route sz13To12 = new Route();
    sz13To12.setStart(sz13);
    sz13To12.add(sz12);
    final List<Unit> transports = sz13.getUnits().getMatches(Matches.unitIsTransport());
    assertEquals(1, transports.size());
    final String error = moveDelegate.move(transports, sz13To12);
    assertEquals(null, error);
  }

  @Test
  public void testBombAndAttackEmptyTerritory() {
    final Territory karrelia = territory("Karelia S.S.R.", gameData);
    final Territory baltic = territory("Baltic States", gameData);
    final Territory sz5 = territory("5 Sea Zone", gameData);
    final Territory germany = territory("Germany", gameData);
    final PlayerID germans = germans(gameData);
    final MoveDelegate moveDelegate = (MoveDelegate) gameData.getDelegateList().getDelegate("move");
    final ITestDelegateBridge bridge = getDelegateBridge(germans);
    bridge.setStepName("CombatMove");
    moveDelegate.setDelegateBridgeAndPlayer(bridge);
    moveDelegate.start();
    when(bridge.getRemotePlayer().shouldBomberBomb(any())).thenReturn(true);
    // remove the russian units
    removeFrom(karrelia, karrelia.getUnits().getMatches(Matches.unitCanBeDamaged().negate()));
    // move the bomber to attack
    move(germany.getUnits().getMatches(Matches.unitIsStrategicBomber()), new Route(germany, sz5, karrelia));
    // move an infantry to invade
    move(baltic.getUnits().getMatches(Matches.unitIsLandTransportable()), new Route(baltic, karrelia));
    final BattleTracker battleTracker = MoveDelegate.getBattleTracker(gameData);
    // we should have a pending land battle, and a pending bombing raid
    assertNotNull(battleTracker.getPendingBattle(karrelia, false, null));
    assertNotNull(battleTracker.getPendingBattle(karrelia, true, null));
    // the territory should not be conquered
    assertEquals(karrelia.getOwner(), russians(gameData));
  }

  @Test
  public void testLingeringSeaUnitsJoinBattle() {
    final Territory sz5 = territory("5 Sea Zone", gameData);
    final Territory sz6 = territory("6 Sea Zone", gameData);
    final Territory sz7 = territory("7 Sea Zone", gameData);
    // add a russian battlship
    addTo(sz5, battleship(gameData).create(1, russians(gameData)));
    final ITestDelegateBridge bridge = getDelegateBridge(germans(gameData));
    bridge.setStepName("CombatMove");
    moveDelegate(gameData).setDelegateBridgeAndPlayer(bridge);
    moveDelegate(gameData).start();
    // attack with a german sub
    move(sz7.getUnits().getUnits(), new Route(sz7, sz6, sz5));
    moveDelegate(gameData).end();
    // adding of lingering units was moved from end of combat-move phase, to start of battle phase
    battleDelegate(gameData).setDelegateBridgeAndPlayer(bridge);
    BattleDelegate.doInitialize(battleDelegate(gameData).getBattleTracker(), bridge);
    // all units in sz5 should be involved in the battle
    final MustFightBattle mfb =
        (MustFightBattle) MoveDelegate.getBattleTracker(gameData).getPendingBattle(sz5, false, null);
    assertEquals(5, mfb.getAttackingUnits().size());
  }

  @Test
  public void testLingeringFightersAndALliedUnitsJoinBattle() {
    final Territory sz5 = territory("5 Sea Zone", gameData);
    final Territory sz6 = territory("6 Sea Zone", gameData);
    final Territory sz7 = territory("7 Sea Zone", gameData);
    // add a russian battlship
    addTo(sz5, battleship(gameData).create(1, russians(gameData)));
    // add an allied carrier and a fighter
    addTo(sz5, carrier(gameData).create(1, italians(gameData)));
    addTo(sz5, fighter(gameData).create(1, germans(gameData)));
    final ITestDelegateBridge bridge = getDelegateBridge(germans(gameData));
    bridge.setStepName("CombatMove");
    moveDelegate(gameData).setDelegateBridgeAndPlayer(bridge);
    moveDelegate(gameData).start();
    // attack with a german sub
    move(sz7.getUnits().getUnits(), new Route(sz7, sz6, sz5));
    moveDelegate(gameData).end();
    // adding of lingering units was moved from end of combat-move phase, to start of battle phase
    battleDelegate(gameData).setDelegateBridgeAndPlayer(bridge);
    BattleDelegate.doInitialize(battleDelegate(gameData).getBattleTracker(), bridge);
    // all units in sz5 should be involved in the battle
    // except the italian carrier
    final MustFightBattle mfb =
        (MustFightBattle) MoveDelegate.getBattleTracker(gameData).getPendingBattle(sz5, false, null);
    assertEquals(6, mfb.getAttackingUnits().size());
  }

  @Test
  public void testLingeringSeaUnitsCanMoveAwayFromBattle() {
    final Territory sz5 = territory("5 Sea Zone", gameData);
    final Territory sz6 = territory("6 Sea Zone", gameData);
    final Territory sz7 = territory("7 Sea Zone", gameData);
    // add a russian battlship
    addTo(sz5, battleship(gameData).create(1, russians(gameData)));
    final ITestDelegateBridge bridge = getDelegateBridge(germans(gameData));
    bridge.setStepName("CombatMove");
    moveDelegate(gameData).setDelegateBridgeAndPlayer(bridge);
    moveDelegate(gameData).start();
    // attack with a german sub
    move(sz7.getUnits().getUnits(), new Route(sz7, sz6, sz5));
    // move the transport away
    move(sz5.getUnits().getMatches(Matches.unitIsTransport()), new Route(sz5, sz6));
    moveDelegate(gameData).end();
    // adding of lingering units was moved from end of combat-move phase, to start of battle phase
    battleDelegate(gameData).setDelegateBridgeAndPlayer(bridge);
    BattleDelegate.doInitialize(battleDelegate(gameData).getBattleTracker(), bridge);
    // all units in sz5 should be involved in the battle
    final MustFightBattle mfb =
        (MustFightBattle) MoveDelegate.getBattleTracker(gameData).getPendingBattle(sz5, false, null);
    assertEquals(4, mfb.getAttackingUnits().size());
  }
}
