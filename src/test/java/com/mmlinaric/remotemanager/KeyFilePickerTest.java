package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.*;

import com.mmlinaric.remotemanager.ui.KeyFilePicker;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KeyFilePickerTest {
    @Test
    void acceptsPastedHomeRelativeKeyPath() {
        assertEquals(
                Path.of(System.getProperty("user.home"), ".ssh", "id_ed25519"),
                KeyFilePicker.parsePath("~/.ssh/id_ed25519"));
        assertNull(KeyFilePicker.parsePath("  "));
    }
}
