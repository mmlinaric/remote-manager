package com.mmlinaric.remotemanager.app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.mmlinaric.remotemanager.ui.SelectionColors;
import com.mmlinaric.remotemanager.ui.settings.Appearance;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies application appearance and the shared Swing defaults that depend on it. */
public final class AppearanceManager {
    private static final Logger LOG = LoggerFactory.getLogger(AppearanceManager.class);

    private AppearanceManager() {}

    /** Applies the requested appearance and returns the effective appearance. */
    public static Appearance apply(Appearance requested) {
        Appearance effective = requested;
        try {
            install(requested);
        } catch (Exception error) {
            LOG.warn("Could not apply {} appearance; using System", requested, error);
            effective = Appearance.SYSTEM;
            installFallback();
        }
        configureDefaults();
        refreshWindows();
        return effective;
    }

    /** Reports whether the active Swing appearance uses a dark application background. */
    public static boolean isDark() {
        Color background = UIManager.getColor("Panel.background");
        if (background == null) return false;
        return SelectionColors.foregroundFor(background).equals(Color.WHITE);
    }

    private static void install(Appearance appearance)
            throws UnsupportedLookAndFeelException, ReflectiveOperationException {
        switch (appearance) {
            case SYSTEM -> installSystemAppearance();
            case LIGHT -> UIManager.setLookAndFeel(new FlatLightLaf());
            case DARK -> UIManager.setLookAndFeel(new FlatDarkLaf());
        }
    }

    private static void installSystemAppearance()
            throws UnsupportedLookAndFeelException, ReflectiveOperationException {
        switch (SystemAppearanceDetector.detect()) {
            case DARK -> UIManager.setLookAndFeel(new FlatDarkLaf());
            case LIGHT -> UIManager.setLookAndFeel(new FlatLightLaf());
            case UNKNOWN -> UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
    }

    private static void installFallback() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception systemError) {
            LOG.warn("Could not apply the system look and feel; using the cross-platform look and feel", systemError);
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception crossPlatformError) {
                LOG.error("Could not apply a fallback look and feel", crossPlatformError);
            }
        }
    }

    private static void configureDefaults() {
        UIManager.put("MenuItem.margin", new Insets(0, 2, 0, 2));
        UIManager.put("MenuItem.checkIcon", new EmptyMenuCheckIcon());
        UIManager.put("MenuItem.afterCheckIconGap", 0);
        UIManager.put("MenuItem.textIconGap", 1);
        SelectionColors.configureTextInputs();
    }

    private static void refreshWindows() {
        for (Window window : Window.getWindows()) {
            if (!window.isDisplayable()) continue;
            SwingUtilities.updateComponentTreeUI(window);
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }
}
