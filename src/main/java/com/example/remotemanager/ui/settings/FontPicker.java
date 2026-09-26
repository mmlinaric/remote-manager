package com.example.remotemanager.ui.settings;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A font dropdown with search inside its popup. */
final class FontPicker extends JButton {
  private final List<String> fonts;
  private final DefaultListModel<String> results = new DefaultListModel<>();
  private final JList<String> list = new JList<>(results);
  private final JScrollPane choices = new JScrollPane(list);
  private final JTextField search = new JTextField(24);
  private final JLabel empty = new JLabel("No matching fonts");
  private final JPopupMenu popup = new JPopupMenu();
  private final int rowHeight;
  private String selectedFont;

  FontPicker(List<String> fonts, String selectedFont) {
    this.fonts = fonts;
    this.selectedFont = selectedFont;
    setText(selectedFont + "  ▾");
    setHorizontalAlignment(LEFT);
    setToolTipText("Choose a terminal font");
    Dimension size = getPreferredSize();
    setPreferredSize(new Dimension(Math.max(size.width, 280), size.height));

    JPanel searchRow = new JPanel(new BorderLayout(0, 4));
    searchRow.add(new JLabel("Search fonts:"), BorderLayout.NORTH);
    searchRow.add(search, BorderLayout.CENTER);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    rowHeight = list.getFontMetrics(list.getFont()).getHeight() + 4;
    list.setFixedCellHeight(rowHeight);
    empty.setVisible(false);
    popup.setLayout(new BorderLayout(0, 6));
    popup.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    popup.add(searchRow, BorderLayout.NORTH);
    popup.add(choices, BorderLayout.CENTER);
    popup.add(empty, BorderLayout.SOUTH);

    search.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent event) { filter(); }
      @Override public void removeUpdate(DocumentEvent event) { filter(); }
      @Override public void changedUpdate(DocumentEvent event) { filter(); }
    });
    search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "nextFont");
    search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previousFont");
    search.getActionMap().put("nextFont", new AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent event) { move(1); }
    });
    search.getActionMap().put("previousFont", new AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent event) { move(-1); }
    });
    search.addActionListener(event -> choose());
    bindEscape(search);
    bindEscape(list);
    list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "chooseFont");
    list.getActionMap().put("chooseFont", new AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent event) { choose(); }
    });
    list.addMouseListener(new MouseAdapter() {
      @Override public void mouseReleased(MouseEvent event) {
        int index = list.locationToIndex(event.getPoint());
        if (index >= 0 && list.getCellBounds(index, index).contains(event.getPoint())) {
          list.setSelectedIndex(index);
          choose();
        }
      }
    });
    addActionListener(event -> {
      search.setText("");
      filter();
      popup.show(this, 0, getHeight());
      SwingUtilities.invokeLater(search::requestFocusInWindow);
    });
  }

  String selectedFont() { return selectedFont; }

  private void bindEscape(JComponent component) {
    component.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeFontList");
    component.getActionMap().put("closeFontList", new AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent event) {
        popup.setVisible(false);
        requestFocusInWindow();
      }
    });
  }

  private void filter() {
    String query = search.getText().strip().toLowerCase(Locale.ROOT);
    results.clear();
    for (String name : fonts) {
      if (name.toLowerCase(Locale.ROOT).contains(query)) results.addElement(name);
    }
    boolean hasResults = !results.isEmpty();
    choices.setVisible(hasResults);
    empty.setVisible(!hasResults);
    if (hasResults) {
      int rows = Math.min(results.size(), 8);
      choices.setPreferredSize(new Dimension(getPreferredSize().width,
          rowHeight * rows + choices.getInsets().top + choices.getInsets().bottom));
    }
    if (results.contains(selectedFont)) list.setSelectedValue(selectedFont, true);
    else if (hasResults) list.setSelectedIndex(0);
    if (popup.isVisible()) popup.pack();
  }

  private void move(int direction) {
    if (results.isEmpty()) return;
    int index = Math.max(0, Math.min(results.size() - 1, list.getSelectedIndex() + direction));
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  private void choose() {
    String choice = list.getSelectedValue();
    if (choice == null) return;
    selectedFont = choice;
    setText(choice + "  ▾");
    popup.setVisible(false);
    requestFocusInWindow();
  }
}
