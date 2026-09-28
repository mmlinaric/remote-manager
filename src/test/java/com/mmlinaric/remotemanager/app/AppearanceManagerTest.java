package com.mmlinaric.remotemanager.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.mmlinaric.remotemanager.ui.settings.Appearance;
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
                assertEquals(Appearance.LIGHT, AppearanceManager.apply(Appearance.LIGHT));
                assertInstanceOf(FlatLightLaf.class, UIManager.getLookAndFeel());
                assertFalse(AppearanceManager.isDark());
                assertSharedDefaults();

                assertEquals(Appearance.DARK, AppearanceManager.apply(Appearance.DARK));
                assertInstanceOf(FlatDarkLaf.class, UIManager.getLookAndFeel());
                assertTrue(AppearanceManager.isDark());
                assertSharedDefaults();

                SystemAppearanceDetector.ColorScheme systemScheme = SystemAppearanceDetector.detect();
                if (systemScheme != SystemAppearanceDetector.ColorScheme.UNKNOWN) {
                    assertEquals(Appearance.SYSTEM, AppearanceManager.apply(Appearance.SYSTEM));
                    assertEquals(
                            systemScheme == SystemAppearanceDetector.ColorScheme.DARK, AppearanceManager.isDark());
                    assertSharedDefaults();
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                try {
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
