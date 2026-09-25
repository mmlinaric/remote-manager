package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.main.MainWindow;
import java.awt.GraphicsEnvironment;
import java.awt.Frame;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainWindowTest {
  @TempDir Path temp;

  @Test void startsLockedWithHostCreationUnavailable() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    SwingUtilities.invokeAndWait(() -> {
      try {
        MainWindow window = new MainWindow(new SettingsRepository(temp.resolve("settings.properties")));
        try {
          window.setVisible(true);
          assertFalse(window.getJMenuBar().getMenu(0).getItem(0).isEnabled());
          assertEquals(Frame.MAXIMIZED_BOTH,
              window.getExtendedState() & Frame.MAXIMIZED_BOTH);
        } finally { window.dispose(); }
      } catch (Exception error) { throw new RuntimeException(error); }
    });
  }
}
