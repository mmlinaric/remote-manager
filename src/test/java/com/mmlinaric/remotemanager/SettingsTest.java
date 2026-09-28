package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.*;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import com.mmlinaric.remotemanager.ui.settings.Appearance;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {
    @TempDir
    Path temp;

    @Test
    void storesNonSecretPreferencesWithoutDatabase() throws Exception {
        Path file = temp.resolve("settings.properties");
        SettingsRepository settings = new SettingsRepository(file);
        assertEquals(Appearance.SYSTEM, AppSettings.load(settings).appearance());
        assertEquals(30, AppSettings.load(settings).vaultAutoLockMinutes());
        settings.put("vault.path", temp.resolve("vault.kdbx").toString());
        settings.put("vault.autoLockMinutes", "0");
        settings.put("ui.appearance", "dark");
        SettingsRepository reopened = new SettingsRepository(file);
        assertEquals(
                temp.resolve("vault.kdbx").toString(),
                reopened.get("vault.path").orElseThrow());
        assertEquals(0, AppSettings.load(reopened).vaultAutoLockMinutes());
        assertEquals(Appearance.DARK, AppSettings.load(reopened).appearance());
    }

    @Test
    void fallsBackToSystemForUnknownAppearance() throws Exception {
        SettingsRepository settings = new SettingsRepository(temp.resolve("settings.properties"));
        settings.put("ui.appearance", "sepia");

        assertEquals(Appearance.SYSTEM, AppSettings.load(settings).appearance());
    }

    @Test
    void savesEachAppearanceUsingItsStableName() throws Exception {
        Path file = temp.resolve("settings.properties");
        for (Appearance appearance : Appearance.values()) {
            SettingsRepository settings = new SettingsRepository(file);
            AppSettings.defaults().withAppearance(appearance).save(settings);

            SettingsRepository reopened = new SettingsRepository(file);
            assertEquals(appearance, AppSettings.load(reopened).appearance());
            assertEquals(appearance.persistedName(), reopened.get("ui.appearance").orElseThrow());
        }
    }
}
