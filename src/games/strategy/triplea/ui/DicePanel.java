package games.strategy.triplea.ui;

import java.awt.Dimension;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import games.strategy.engine.data.GameData;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.delegate.Die;

public class DicePanel extends JPanel {
  private static final long serialVersionUID = -7544999867518263506L;
  private final IUIContext m_uiContext;
  private final GameData m_data;

  public DicePanel(final IUIContext uiContext, final GameData data) {
    m_uiContext = uiContext;
    m_data = data;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
  }

  public void clear() {
    removeAll();
  }

  public void setDiceRollForBombing(final List<Die> dice, final int cost) {
    removeAll();
    add(create(dice, -1));
    add(Box.createVerticalGlue());
    add(new JLabel("Cost:" + cost));
    invalidate();
  }

  public void setDiceRoll(final DiceRoll diceRoll) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          setDiceRoll(diceRoll);
        }
      });
      return;
    }
    removeAll();
    for (int i = 1; i <= m_data.getDiceSides(); i++) {
      final List<Die> dice = diceRoll.getRolls(i);
      if (dice.isEmpty()) {
        continue;
      }
      add(new JLabel("Rolled at " + (i) + ":"));
      add(create(diceRoll.getRolls(i), i));
    }
    add(Box.createVerticalGlue());
    add(new JLabel("Total hits:" + diceRoll.getHits()));
    validate();
    invalidate();
    repaint();
  }

  private JComponent create(final List<Die> dice, final int rollAt) {
    final JPanel dicePanel = new JPanel();
    dicePanel.setLayout(new BoxLayout(dicePanel, BoxLayout.X_AXIS));
    dicePanel.add(Box.createHorizontalStrut(20));
    for (final Die die : dice) {
      final int roll = die.getValue() + 1;
      dicePanel.add(new JLabel(m_uiContext.getDiceImageFactory().getDieIcon(roll, die.getType())));
      dicePanel.add(Box.createHorizontalStrut(2));
    }
    final JScrollPane scroll = new JScrollPane(dicePanel);
    scroll.setBorder(null);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    // we're adding to a box layout, so to prevent the component from
    // grabbing extra space, set the max height.
    // allow room for a dice and a scrollbar
    scroll.setMinimumSize(new Dimension(scroll.getMinimumSize().width, m_uiContext.getDiceImageFactory().DIE_HEIGHT + 17));
    scroll.setMaximumSize(new Dimension(scroll.getMaximumSize().width, m_uiContext.getDiceImageFactory().DIE_HEIGHT + 17));
    scroll.setPreferredSize(new Dimension(scroll.getPreferredSize().width, m_uiContext.getDiceImageFactory().DIE_HEIGHT + 17));
    return scroll;
  }
}
