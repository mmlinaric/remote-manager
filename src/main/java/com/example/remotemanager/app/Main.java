package com.example.remotemanager.app;

import com.example.remotemanager.persistence.ConnectionRepository;
import com.example.remotemanager.persistence.Database;
import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.main.MainWindow;
import java.nio.file.Files;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main() {}

  public static void main(String[] args) {
    try {
      Files.createDirectories(AppPaths.dataDirectory());
      Database database = new Database(AppPaths.dataDirectory().resolve("connections.db"));
      database.migrate();
      SwingUtilities.invokeLater(() -> showWindow(database));
    } catch (Exception error) {
      LOG.error("Could not initialize application database", error);
      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  null,
                  "Could not initialize application database: " + error.getMessage(),
                  "Startup error",
                  JOptionPane.ERROR_MESSAGE));
    }
  }

  private static void showWindow(Database database) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception error) {
      LOG.warn("Could not select the system look and feel", error);
    }
    new MainWindow(new ConnectionRepository(database), new SettingsRepository(database))
        .setVisible(true);
  }
}
