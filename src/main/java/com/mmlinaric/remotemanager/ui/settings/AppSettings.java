package com.mmlinaric.remotemanager.ui.settings;

import com.mmlinaric.remotemanager.app.AppPaths;
import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import java.io.IOException;
import java.nio.file.Path;

/** Immutable non-secret desktop preferences applied to sessions, updates, and vault activity handling. */
public record AppSettings(
        String terminalFont,
        int terminalFontSize,
        int scrollbackLines,
        int clipboardSeconds,
        int vaultAutoLockMinutes,
        Path knownHosts) {

    public static AppSettings defaults() {
        return new AppSettings("Monospaced", 13, 5000, 30, 30, AppPaths.knownHosts());
    }

    public static AppSettings load(SettingsRepository repository) {
        AppSettings defaults = defaults();
        return new AppSettings(
                repository.get("terminal.font").orElse(defaults.terminalFont()),
                number(repository, "terminal.fontSize", defaults.terminalFontSize()),
                number(repository, "terminal.scrollback", defaults.scrollbackLines()),
                number(repository, "clipboard.seconds", defaults.clipboardSeconds()),
                autoLock(repository, defaults.vaultAutoLockMinutes()),
                Path.of(repository
                        .get("ssh.knownHosts")
                        .orElse(defaults.knownHosts().toString())));
    }

    public void save(SettingsRepository repository) throws IOException {
        repository.put("terminal.font", terminalFont);
        repository.put("terminal.fontSize", Integer.toString(terminalFontSize));
        repository.put("terminal.scrollback", Integer.toString(scrollbackLines));
        repository.put("clipboard.seconds", Integer.toString(clipboardSeconds));
        repository.put("vault.autoLockMinutes", Integer.toString(vaultAutoLockMinutes));
        repository.put("ssh.knownHosts", knownHosts.toString());
    }

    private static int number(SettingsRepository repository, String key, int fallback) {
        try {
            return repository.get(key).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static int autoLock(SettingsRepository repository, int fallback) {
        int value = number(repository, "vault.autoLockMinutes", fallback);
        return value == 0 || value == 5 || value == 15 || value == 30 ? value : fallback;
    }
}
