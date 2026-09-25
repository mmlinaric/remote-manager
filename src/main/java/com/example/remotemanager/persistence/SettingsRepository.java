package com.example.remotemanager.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Optional;
import java.util.Properties;

/** Local non-secret preferences. Vault contents never enter this file. */
public final class SettingsRepository {
  private final Path file;
  private final Properties values = new Properties();

  public SettingsRepository(Path file) throws IOException {
    this.file = file;
    if (Files.exists(file)) {
      try (InputStream input = Files.newInputStream(file)) { values.load(input); }
    }
  }

  public synchronized Optional<String> get(String key) {
    return Optional.ofNullable(values.getProperty(key));
  }

  public synchronized void put(String key, String value) throws IOException {
    values.setProperty(key, value);
    Files.createDirectories(file.toAbsolutePath().getParent());
    Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".settings-", ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        values.store(output, "Remote Manager preferences (no credentials)");
      }
      try {
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally { Files.deleteIfExists(temporary); }
  }
}
