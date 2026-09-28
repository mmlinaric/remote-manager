package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.*;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
import com.mmlinaric.remotemanager.ui.settings.Appearance;
import com.mmlinaric.remotemanager.ui.settings.InterfaceScale;
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
        assertEquals(InterfaceScale.PERCENT_100, AppSettings.load(settings).interfaceScale());
        assertEquals(30, AppSettings.load(settings).vaultAutoLockMinutes());
        settings.put("vault.path", temp.resolve("vault.kdbx").toString());
        settings.put("vault.autoLockMinutes", "0");
        settings.put("ui.appearance", "dark");
        settings.put("ui.scale", "150");
        SettingsRepository reopened = new SettingsRepository(file);
        assertEquals(
                temp.resolve("vault.kdbx").toString(),
                reopened.get("vault.path").orElseThrow());
        assertEquals(0, AppSettings.load(reopened).vaultAutoLockMinutes());
        assertEquals(Appearance.DARK, AppSettings.load(reopened).appearance());
        assertEquals(InterfaceScale.PERCENT_150, AppSettings.load(reopened).interfaceScale());
    }

    @Test
    void fallsBackToSystemForUnknownAppearance() throws Exception {
        SettingsRepository settings = new SettingsRepository(temp.resolve("settings.properties"));
        settings.put("ui.appearance", "sepia");

        assertEquals(Appearance.SYSTEM, AppSettings.load(settings).appearance());
    }

    @Test
    void fallsBackToDefaultForInvalidInterfaceScale() throws Exception {
        SettingsRepository settings = new SettingsRepository(temp.resolve("settings.properties"));
        for (String invalid : new String[] {"135", "large", ""}) {
            settings.put("ui.scale", invalid);
            assertEquals(InterfaceScale.PERCENT_100, AppSettings.load(settings).interfaceScale());
        }
    }

    @Test
    void savesEachAppearanceUsingItsStableName() throws Exception {
        Path file = temp.resolve("settings.properties");
        for (Appearance appearance : Appearance.values()) {
            SettingsRepository settings = new SettingsRepository(file);
            AppSettings.defaults().withAppearance(appearance).save(settings);

            SettingsRepository reopened = new SettingsRepository(file);
            assertEquals(appearance, AppSettings.load(reopened).appearance());
            assertEquals(
                    appearance.persistedName(), reopened.get("ui.appearance").orElseThrow());
        }
    }

    @Test
    void savesEachInterfaceScaleUsingItsStablePercentage() throws Exception {
        Path file = temp.resolve("settings.properties");
        for (InterfaceScale scale : InterfaceScale.values()) {
            SettingsRepository settings = new SettingsRepository(file);
            AppSettings.defaults().withInterfaceScale(scale).save(settings);

            SettingsRepository reopened = new SettingsRepository(file);
            assertEquals(scale, AppSettings.load(reopened).interfaceScale());
            assertEquals(scale.persistedName(), reopened.get("ui.scale").orElseThrow());
        }
    }
}
