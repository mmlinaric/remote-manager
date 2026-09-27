package com.example.remotemanager.app;

import java.util.Optional;

/** Version metadata embedded by Maven in packaged builds. */
public final class AppVersion {
  private static final String DEVELOPMENT = "development";

  private AppVersion() {}

  public static String display() {
    return packaged().orElse(DEVELOPMENT);
  }

  public static Optional<String> packaged() {
    String version = Main.class.getPackage().getImplementationVersion();
    if (version == null || version.isBlank() || version.endsWith("-SNAPSHOT")) return Optional.empty();
    return Optional.of(version);
  }
}
