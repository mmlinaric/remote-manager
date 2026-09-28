package com.mmlinaric.remotemanager.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Reads the Linux desktop color preference when Swing cannot discover it itself. */
final class SystemAppearanceDetector {
    enum ColorScheme {
        DARK,
        LIGHT,
        UNKNOWN
    }

    private SystemAppearanceDetector() {}

    static ColorScheme detect() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return ColorScheme.UNKNOWN;
        }
        return fromGnomeSettings(
                readSetting("org.gnome.desktop.interface", "color-scheme").orElse(""),
                readSetting("org.gnome.desktop.interface", "gtk-theme").orElse(""));
    }

    static ColorScheme fromGnomeSettings(String colorScheme, String gtkTheme) {
        String normalizedScheme = normalized(colorScheme);
        if (normalizedScheme.equals("prefer-dark")) return ColorScheme.DARK;
        if (normalizedScheme.equals("prefer-light")) return ColorScheme.LIGHT;

        String normalizedTheme = normalized(gtkTheme);
        if (normalizedTheme.contains("dark")) return ColorScheme.DARK;
        if (normalizedScheme.equals("default") || !normalizedTheme.isBlank()) return ColorScheme.LIGHT;
        return ColorScheme.UNKNOWN;
    }

    private static Optional<String> readSetting(String schema, String key) {
        Process process = null;
        try {
            process = new ProcessBuilder("gsettings", "get", schema, key)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) return Optional.empty();
            return Optional.of(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String normalized(String value) {
        return value.trim().replace("'", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }
}
