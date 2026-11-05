package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.main.MainWindow;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainWindowTest {
    @TempDir
    Path temp;

    @Test
    void startsLockedWithHostCreationUnavailable() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            try {
                SettingsRepository settings = new SettingsRepository(temp.resolve("settings.properties"));
                MainWindow window = new MainWindow(settings, AppSettings.load(settings));
                try {
                    window.setVisible(true);
                    assertFalse(window.getJMenuBar().getMenu(0).getItem(0).isEnabled());
                    if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
                        assertEquals(Frame.MAXIMIZED_BOTH, window.getExtendedState() & Frame.MAXIMIZED_BOTH);
                    }
                } finally {
                    window.dispose();
                }
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }
}
