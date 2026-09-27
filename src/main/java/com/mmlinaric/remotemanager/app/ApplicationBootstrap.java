package com.mmlinaric.remotemanager.app;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.SelectionColors;
import com.mmlinaric.remotemanager.ui.main.MainWindow;
import java.nio.file.Files;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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
        configureSwingDefaults();
        MainWindow window = new MainWindow(settings);
        DesktopWindowConfigurator.configure(window);
        window.setVisible(true);
        window.checkForUpdatesAutomatically();
    }

    private void configureSwingDefaults() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception error) {
            LOG.warn("Could not select the system look and feel", error);
        }
        UIManager.put("MenuItem.margin", new java.awt.Insets(0, 2, 0, 2));
        UIManager.put("MenuItem.checkIcon", new EmptyMenuCheckIcon());
        UIManager.put("MenuItem.afterCheckIconGap", 0);
        UIManager.put("MenuItem.textIconGap", 1);
        SelectionColors.configureTextInputs();
    }
}
