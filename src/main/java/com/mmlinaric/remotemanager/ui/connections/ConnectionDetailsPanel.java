package com.mmlinaric.remotemanager.ui.connections;

import com.mmlinaric.remotemanager.model.Connection;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** Compact summary of the selected connection in the tree. */
public final class ConnectionDetailsPanel extends JPanel {
  private final JLabel name = new JLabel("Name: ");
  private final JLabel host = new JLabel("Host: ");
  private final JLabel user = new JLabel("User: ");
  private final JLabel port = new JLabel("Port: ");
  private final JLabel authentication = new JLabel("Authentication: ");
  private final JLabel credential = new JLabel("Credential: ");
  private final CardLayout cards = new CardLayout();
  private final JPanel content = new JPanel(cards);
  private final JTextArea empty = new JTextArea("Select a host to view its configuration.", 3, 18);

  public ConnectionDetailsPanel() {
    super(new BorderLayout());
    JPanel values = new JPanel(new GridLayout(6, 1, 0, 2));
    values.add(name);
    values.add(host);
    values.add(user);
    values.add(port);
    values.add(authentication);
    values.add(credential);
    JPanel summary = new JPanel(new BorderLayout());
    summary.add(new JLabel("Configuration"), BorderLayout.NORTH);
    summary.add(values, BorderLayout.CENTER);
    empty.setEditable(false);
    empty.setFocusable(false);
    empty.setOpaque(false);
    empty.setLineWrap(true);
    empty.setWrapStyleWord(true);
    empty.setFont(UIManager.getFont("Label.font"));
    empty.setForeground(UIManager.getColor("Label.foreground"));
    empty.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    content.add(summary, "host");
    content.add(empty, "empty");
    add(content, BorderLayout.CENTER);
    showConnection(null);
  }

  public void showConnection(Connection connection) {
    showConnection(connection, null);
  }

  public void showConnection(Connection connection, String issue) {
    cards.show(content, connection == null ? "empty" : "host");
    if (connection == null) return;
    name.setText("Name: " + connection.name());
    host.setText("Host: " + connection.hostname());
    user.setText("User: " + connection.username());
    port.setText("Port: " + connection.port());
    authentication.setText("Authentication: " + connection.authenticationType());
    credential.setText("Credential: " + (issue != null ? issue
        : connection.sshCredentialEntryId() == null ? "No vault identity needed" : "Ready"));
  }
}
