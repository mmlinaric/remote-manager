package com.mmlinaric.remotemanager.ui.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import org.junit.jupiter.api.Test;

class DarkTerminalPaletteTest {
    private static final Color CANVAS = new Color(0x1E1E1E);
    private static final Color BLACK = new Color(0x000000);
    private static final Color WHITE = new Color(0xCCCCCC);
    private static final Color BRIGHT_WHITE = new Color(0xFFFFFF);
    private static final double MINIMUM_CONTRAST = 4.5;

    @Test
    void everyTextColorIsReadableOnTheCanvas() {
        // Index 0 is excluded: applications use black text on colored backgrounds, not on the default canvas.
        for (int index = 1; index < 16; index++) {
            assertReadable(foreground(index), CANVAS, "foreground " + index);
        }
    }

    @Test
    void backgroundsKeepTheirConventionalTextReadable() {
        for (int index : new int[] {1, 4, 5}) {
            assertReadable(WHITE, background(index), "white text on background " + index);
        }
        for (int index : new int[] {2, 3, 6, 7, 10, 11, 14, 15}) {
            assertReadable(BLACK, background(index), "black text on background " + index);
        }
        for (int index : new int[] {8, 9, 12, 13}) {
            assertReadable(BRIGHT_WHITE, background(index), "bright white text on background " + index);
        }
    }

    private static Color foreground(int index) {
        return DarkTerminalPalette.INSTANCE.getForeground(new TerminalColor(index));
    }

    private static Color background(int index) {
        return DarkTerminalPalette.INSTANCE.getBackground(new TerminalColor(index));
    }

    private static void assertReadable(Color text, Color background, String description) {
        double contrast = contrast(text, background);
        assertTrue(contrast >= MINIMUM_CONTRAST, description + " has contrast " + contrast);
    }

    /** WCAG 2 contrast ratio. */
    private static double contrast(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
