package com.mmlinaric.remotemanager.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Local folder display state, kept separate for each vault path. */
public final class FolderExpansionPreferences {
  private final SettingsRepository settings;

  public FolderExpansionPreferences(SettingsRepository settings) {
    this.settings = settings;
  }

  public String load(Path vaultPath) {
    return settings.get(key(vaultPath)).orElse("");
  }

  public void save(Path vaultPath, String expandedFolderIds) throws IOException {
    settings.put(key(vaultPath), expandedFolderIds);
  }

  private static String key(Path vaultPath) {
    try {
      byte[] path = vaultPath.toAbsolutePath().normalize().toString()
          .getBytes(StandardCharsets.UTF_8);
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(path);
      return "ui.expandedFolders." + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }
}
