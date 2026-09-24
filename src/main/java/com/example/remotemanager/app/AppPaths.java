package com.example.remotemanager.app;

import java.nio.file.Path;

public final class AppPaths {
  private AppPaths() {}

  public static Path dataDirectory() {
    String os = System.getProperty("os.name").toLowerCase();
    String home = System.getProperty("user.home");
    if (os.contains("win")) {
      String appData = System.getenv("APPDATA");
      return Path.of(appData == null ? home : appData, "RemoteManager");
    }
    if (os.contains("mac")) {
      return Path.of(home, "Library", "Application Support", "RemoteManager");
    }
    String xdg = System.getenv("XDG_DATA_HOME");
    Path dataHome = xdg == null || xdg.isBlank() ? Path.of(home, ".local", "share") : Path.of(xdg);
    return dataHome.resolve("remote-manager");
  }

  public static Path knownHosts() {
    return Path.of(System.getProperty("user.home"), ".ssh", "known_hosts");
  }
}
