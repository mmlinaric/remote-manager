package com.mmlinaric.remotemanager.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import com.mmlinaric.remotemanager.ui.SilkIcons;
import com.mmlinaric.remotemanager.ui.settings.Appearance;
import com.mmlinaric.remotemanager.ui.settings.InterfaceScale;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class AppearanceManagerTest {
    @Test
    void appliesLightAndDarkAppearancesWithSharedDefaults() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(
                        Appearance.LIGHT,
                        AppearanceManager.apply(Appearance.LIGHT, InterfaceScale.PERCENT_100));
                assertInstanceOf(FlatLightLaf.class, UIManager.getLookAndFeel());
                assertFalse(AppearanceManager.isDark());
                assertSharedDefaults();

                assertEquals(
                        Appearance.DARK,
                        AppearanceManager.apply(Appearance.DARK, InterfaceScale.PERCENT_100));
                assertInstanceOf(FlatDarkLaf.class, UIManager.getLookAndFeel());
                assertTrue(AppearanceManager.isDark());
                assertSharedDefaults();

                SystemAppearanceDetector.ColorScheme systemScheme = SystemAppearanceDetector.detect();
                if (systemScheme != SystemAppearanceDetector.ColorScheme.UNKNOWN) {
                    assertEquals(
                            Appearance.SYSTEM,
                            AppearanceManager.apply(Appearance.SYSTEM, InterfaceScale.PERCENT_100));
                    assertEquals(
                            systemScheme == SystemAppearanceDetector.ColorScheme.DARK, AppearanceManager.isDark());
                    assertSharedDefaults();
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIScale.setZoomFactor(1f);
                    UIManager.setLookAndFeel(previous);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    @Test
    void appliesScaleToFontsAndIconsWithoutCompoundingAcrossAppearanceChanges() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            SwingUtilities.invokeAndWait(() -> {
                AppearanceManager.apply(Appearance.LIGHT, InterfaceScale.PERCENT_100);
                int baseFontSize = UIManager.getFont("Label.font").getSize();
                int baseIconSize = SilkIcons.CONNECTION.getIconWidth();

                AppearanceManager.apply(Appearance.LIGHT, InterfaceScale.PERCENT_150);
                int scaledFontSize = UIManager.getFont("Label.font").getSize();
                int scaledIconSize = SilkIcons.CONNECTION.getIconWidth();
                assertTrue(scaledFontSize > baseFontSize);
                assertTrue(scaledIconSize > baseIconSize);
                assertEquals(1.5f, UIScale.getZoomFactor());

                AppearanceManager.apply(Appearance.DARK, InterfaceScale.PERCENT_150);
                assertEquals(scaledFontSize, UIManager.getFont("Label.font").getSize());
                assertEquals(scaledIconSize, SilkIcons.CONNECTION.getIconWidth());

                AppearanceManager.apply(Appearance.LIGHT, InterfaceScale.PERCENT_100);
                assertEquals(baseFontSize, UIManager.getFont("Label.font").getSize());
                assertEquals(baseIconSize, SilkIcons.CONNECTION.getIconWidth());
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIScale.setZoomFactor(1f);
                    UIManager.setLookAndFeel(previous);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    private static void assertSharedDefaults() {
        assertInstanceOf(EmptyMenuCheckIcon.class, UIManager.getIcon("MenuItem.checkIcon"));
        assertNotNull(UIManager.getColor("TextField.selectionForeground"));
    }
}
