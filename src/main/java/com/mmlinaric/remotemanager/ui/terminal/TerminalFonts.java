package com.mmlinaric.remotemanager.ui.terminal;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/** Chooses a font whose printable ASCII glyphs fit JediTerm's fixed-width cells. */
public final class TerminalFonts {
  private static final List<String> FALLBACKS = List.of(
      "Consolas", "Menlo", "Liberation Mono", "Adwaita Mono",
      "DejaVu Sans Mono", "Courier New", "Noto Sans Mono");

  private TerminalFonts() {}

  public static List<String> availableMonospacedFamilies() {
    return AvailableFamilies.NAMES;
  }

  private static final class AvailableFamilies {
    private static final List<String> NAMES = scanAvailableMonospacedFamilies();
  }

  private static List<String> scanAvailableMonospacedFamilies() {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
              .getAvailableFontFamilyNames())
          .filter(name -> fitsCells(new Font(name, Font.PLAIN, 13), graphics))
          .sorted(String.CASE_INSENSITIVE_ORDER)
          .toList();
    } finally {
      graphics.dispose();
      image.flush();
    }
  }

  public static Font resolve(String requestedName, int size) {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      Font requested = new Font(requestedName, Font.PLAIN, size);
      if (fitsCells(requested, graphics)) return requested;

      String[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getAvailableFontFamilyNames();
      for (String candidate : FALLBACKS) {
        if (Arrays.stream(installed).anyMatch(candidate::equalsIgnoreCase)) {
          Font fallback = new Font(candidate, Font.PLAIN, size);
          if (fitsCells(fallback, graphics)) return fallback;
        }
      }
      // Other systems may provide a different physical monospace family.
      for (String candidate : installed) {
        if (candidate.toLowerCase(java.util.Locale.ROOT).matches(
            ".*(mono|courier|console|code|typewriter|fixed).*")) {
          Font fallback = new Font(candidate, Font.PLAIN, size);
          if (fitsCells(fallback, graphics)) return fallback;
        }
      }
      for (String candidate : installed) {
        Font fallback = new Font(candidate, Font.PLAIN, size);
        if (fitsCells(fallback, graphics)) return fallback;
      }
      return requested;
    } finally {
      graphics.dispose();
      image.flush();
    }
  }

  private static boolean fitsCells(Font font, Graphics2D graphics) {
    FontMetrics regular = graphics.getFontMetrics(font);
    FontMetrics bold = graphics.getFontMetrics(font.deriveFont(Font.BOLD));
    int cellWidth = regular.charWidth('W');
    if (cellWidth <= 0) return false;
    for (char character = ' '; character <= '~'; character++) {
      if (!font.canDisplay(character)
          || regular.charWidth(character) != cellWidth
          || bold.charWidth(character) != cellWidth) return false;
    }
    return true;
  }
}
