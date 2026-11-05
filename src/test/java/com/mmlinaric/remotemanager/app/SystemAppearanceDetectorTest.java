package com.mmlinaric.remotemanager.app;

import static com.mmlinaric.remotemanager.app.SystemAppearanceDetector.ColorScheme.DARK;
import static com.mmlinaric.remotemanager.app.SystemAppearanceDetector.ColorScheme.LIGHT;
import static com.mmlinaric.remotemanager.app.SystemAppearanceDetector.ColorScheme.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SystemAppearanceDetectorTest {
    @Test
    void recognizesModernGnomeColorPreferences() {
        assertEquals(DARK, SystemAppearanceDetector.fromGnomeSettings("'prefer-dark'", "'Adwaita'"));
        assertEquals(LIGHT, SystemAppearanceDetector.fromGnomeSettings("'prefer-light'", "'Adwaita-dark'"));
        assertEquals(LIGHT, SystemAppearanceDetector.fromGnomeSettings("'default'", "'Adwaita'"));
    }

    @Test
    void usesTheGtkThemeForOlderGnomeVersions() {
        assertEquals(DARK, SystemAppearanceDetector.fromGnomeSettings("", "'Adwaita-dark'"));
        assertEquals(LIGHT, SystemAppearanceDetector.fromGnomeSettings("", "'Adwaita'"));
        assertEquals(UNKNOWN, SystemAppearanceDetector.fromGnomeSettings("", ""));
    }
}
