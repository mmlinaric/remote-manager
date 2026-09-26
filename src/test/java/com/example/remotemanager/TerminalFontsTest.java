package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.remotemanager.ui.terminal.TerminalFonts;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class TerminalFontsTest {
  @Test
  void defaultFontDoesNotOverlapTheNextPromptCharacter() {
    Font font = TerminalFonts.resolve("Monospaced", 13);
    Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      FontMetrics regular = graphics.getFontMetrics(font);
      FontMetrics bold = graphics.getFontMetrics(font.deriveFont(Font.BOLD));
      int cellWidth = regular.charWidth('W');
      assertEquals(cellWidth, bold.charWidth('m'));
      assertEquals(cellWidth, bold.charWidth('a'));
    } finally {
      graphics.dispose();
    }
  }
}
