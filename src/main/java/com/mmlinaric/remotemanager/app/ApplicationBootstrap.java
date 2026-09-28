package com.mmlinaric.remotemanager.app;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.main.MainWindow;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import java.nio.file.Files;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configures process-wide Swing state and creates the first application window. */
final class ApplicationBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationBootstrap.class);

    void launch() {
        try {
            Files.createDirectories(AppPaths.dataDirectory());
            SettingsRepository settings =
                    new SettingsRepository(AppPaths.dataDirectory().resolve("settings.properties"));
            SwingUtilities.invokeLater(() -> showWindow(settings));
        } catch (Exception error) {
            LOG.error("Could not initialize application settings", error);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null,
                    "Could not initialize application settings: " + error.getMessage(),
                    "Startup error",
                    JOptionPane.ERROR_MESSAGE));
        }
    }

    private void showWindow(SettingsRepository settings) {
        AppSettings preferences = AppSettings.load(settings);
        preferences = preferences.withAppearance(AppearanceManager.apply(preferences.appearance()));
        MainWindow window = new MainWindow(settings, preferences);
        DesktopWindowConfigurator.configure(window);
        window.setVisible(true);
        window.checkForUpdatesAutomatically();
    }
}
