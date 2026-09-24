package com.example.remotemanager.ui.settings;

import com.example.remotemanager.app.AppPaths;
import com.example.remotemanager.persistence.SettingsRepository;
import java.nio.file.Path;
import java.sql.SQLException;

public record AppSettings(
    String terminalFont,
    int terminalFontSize,
    int scrollbackLines,
    int clipboardSeconds,
    int vaultAutoLockMinutes,
    Path knownHosts) {

  public static AppSettings defaults() {
    return new AppSettings("Monospaced", 13, 5000, 30, 0, AppPaths.knownHosts());
  }

  public static AppSettings load(SettingsRepository repository) throws SQLException {
    AppSettings defaults = defaults();
    return new AppSettings(
        repository.get("terminal.font").orElse(defaults.terminalFont()),
        number(repository, "terminal.fontSize", defaults.terminalFontSize()),
        number(repository, "terminal.scrollback", defaults.scrollbackLines()),
        number(repository, "clipboard.seconds", defaults.clipboardSeconds()),
        number(repository, "vault.autoLockMinutes", defaults.vaultAutoLockMinutes()),
        Path.of(repository.get("ssh.knownHosts").orElse(defaults.knownHosts().toString())));
  }

  public void save(SettingsRepository repository) throws SQLException {
    repository.put("terminal.font", terminalFont);
    repository.put("terminal.fontSize", Integer.toString(terminalFontSize));
    repository.put("terminal.scrollback", Integer.toString(scrollbackLines));
    repository.put("clipboard.seconds", Integer.toString(clipboardSeconds));
    repository.put("vault.autoLockMinutes", Integer.toString(vaultAutoLockMinutes));
    repository.put("ssh.knownHosts", knownHosts.toString());
  }

  private static int number(SettingsRepository repository, String key, int fallback)
      throws SQLException {
    try {
      return repository.get(key).map(Integer::parseInt).orElse(fallback);
    } catch (NumberFormatException invalid) {
      return fallback;
    }
  }
}
