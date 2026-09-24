package com.example.remotemanager.app;

import com.example.remotemanager.persistence.ConnectionRepository;
import com.example.remotemanager.persistence.Database;
import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.main.MainWindow;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main() {}

  public static void main(String[] args) {
    CompletableFuture.supplyAsync(
            () -> {
              try {
                Files.createDirectories(AppPaths.dataDirectory());
                Database database =
                    new Database(AppPaths.dataDirectory().resolve("connections.db"));
                database.migrate();
                return database;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .whenComplete(
            (database, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error != null) {
                        JOptionPane.showMessageDialog(
                            null,
                            "Could not initialize application database: "
                                + error.getCause().getMessage(),
                            "Startup error",
                            JOptionPane.ERROR_MESSAGE);
                        return;
                      }
                      try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                      } catch (Exception lookAndFeelError) {
                        LOG.warn("Could not select the system look and feel", lookAndFeelError);
                      }
                      new MainWindow(
                              new ConnectionRepository(database), new SettingsRepository(database))
                          .setVisible(true);
                    }));
  }
}
