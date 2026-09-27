package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.*;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import com.mmlinaric.remotemanager.ui.settings.AppSettings;
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
        assertEquals(30, AppSettings.load(settings).vaultAutoLockMinutes());
        settings.put("vault.path", temp.resolve("vault.kdbx").toString());
        settings.put("vault.autoLockMinutes", "0");
        SettingsRepository reopened = new SettingsRepository(file);
        assertEquals(
                temp.resolve("vault.kdbx").toString(),
                reopened.get("vault.path").orElseThrow());
        assertEquals(0, AppSettings.load(reopened).vaultAutoLockMinutes());
    }
}
