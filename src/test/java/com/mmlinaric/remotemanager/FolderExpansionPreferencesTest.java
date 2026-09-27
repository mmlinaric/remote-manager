package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mmlinaric.remotemanager.persistence.FolderExpansionPreferences;
import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderExpansionPreferencesTest {
    @TempDir
    Path temp;

    @Test
    void remembersSeparateLayoutsForEachVault() throws Exception {
        Path file = temp.resolve("settings.properties");
        Path first = temp.resolve("first.kdbx");
        Path second = temp.resolve("second.kdbx");
        FolderExpansionPreferences preferences = new FolderExpansionPreferences(new SettingsRepository(file));
        preferences.save(first, "one,two");
        preferences.save(second, "three");

        FolderExpansionPreferences reopened = new FolderExpansionPreferences(new SettingsRepository(file));
        assertEquals("one,two", reopened.load(first));
        assertEquals(
                "one,two", reopened.load(temp.resolve("folder").resolve("..").resolve("first.kdbx")));
        assertEquals("three", reopened.load(second));
        assertEquals("", reopened.load(temp.resolve("unknown.kdbx")));
    }
}
