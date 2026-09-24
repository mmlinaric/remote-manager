package com.example.remotemanager.ui.connections;

import com.example.remotemanager.model.Connection;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Compact summary of the selected connection in the tree. */
public final class ConnectionDetailsPanel extends JPanel {
  private final JLabel name = new JLabel();
  private final JLabel host = new JLabel();
  private final JLabel user = new JLabel();
  private final JLabel port = new JLabel();

  public ConnectionDetailsPanel() {
    super(new BorderLayout());
    JPanel values = new JPanel(new GridLayout(4, 1, 0, 2));
    values.add(name);
    values.add(host);
    values.add(user);
    values.add(port);
    add(new JLabel("Configuration"), BorderLayout.NORTH);
    add(values, BorderLayout.CENTER);
    showConnection(null);
  }

  public void showConnection(Connection connection) {
    name.setText("Name: " + value(connection == null ? null : connection.name()));
    host.setText("Host: " + value(connection == null ? null : connection.hostname()));
    user.setText("User: " + value(connection == null ? null : connection.username()));
    port.setText("Port: " + (connection == null ? "" : connection.port()));
  }

  private static String value(String text) {
    return text == null ? "" : text;
  }
}
