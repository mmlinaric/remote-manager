package com.mmlinaric.remotemanager.app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import com.mmlinaric.remotemanager.ui.SelectionColors;
import com.mmlinaric.remotemanager.ui.settings.Appearance;
import com.mmlinaric.remotemanager.ui.settings.InterfaceScale;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.UIDefaults;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies application appearance and the shared Swing defaults that depend on it. */
public final class AppearanceManager {
    private static final Logger LOG = LoggerFactory.getLogger(AppearanceManager.class);

    private AppearanceManager() {}

    /** Applies the requested visual settings and returns the effective appearance. */
    public static Appearance apply(Appearance requested, InterfaceScale scale) {
        Appearance effective = requested;
        try {
            install(requested);
        } catch (Exception error) {
            LOG.warn("Could not apply {} appearance; using System", requested, error);
            effective = Appearance.SYSTEM;
            installFallback();
        }
        applyScale(scale);
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

    private static void applyScale(InterfaceScale scale) {
        UIScale.setZoomFactor(scale.factor());
        if (!(UIManager.getLookAndFeel() instanceof FlatLaf)) {
            scaleNativeFonts(scale.factor());
        }
    }

    private static void scaleNativeFonts(float factor) {
        if (factor == 1f) return;
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        List<Object> fontKeys = new ArrayList<>();
        for (Object key : defaults.keySet()) {
            if (defaults.get(key) instanceof FontUIResource) fontKeys.add(key);
        }
        for (Object key : fontKeys) {
            Font font = defaults.getFont(key);
            if (font != null) {
                defaults.put(key, new FontUIResource(font.deriveFont(font.getSize2D() * factor)));
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
