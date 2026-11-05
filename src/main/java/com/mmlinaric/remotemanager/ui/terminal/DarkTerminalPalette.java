package com.mmlinaric.remotemanager.ui.terminal;

import com.jediterm.core.Color;
import com.jediterm.terminal.emulator.ColorPalette;

/**
 * ANSI colors for the dark terminal canvas, used instead of JediTerm's platform defaults because its Windows palette
 * renders blue at about 1:1 contrast on that canvas. Foregrounds stay readable as text on the canvas; backgrounds are
 * deeper so the text that applications usually place on them stays readable too.
 */
public final class DarkTerminalPalette extends ColorPalette {
    public static final ColorPalette INSTANCE = new DarkTerminalPalette();

    private static final Color[] FOREGROUNDS = {
        new Color(0x000000), // black
        new Color(0xF14C4C), // red
        new Color(0x23D18B), // green
        new Color(0xE5E510), // yellow
        new Color(0x569CD6), // blue
        new Color(0xD670D6), // magenta
        new Color(0x29B8DB), // cyan
        new Color(0xCCCCCC), // white
        new Color(0x8C8C8C), // bright black
        new Color(0xFF7B72), // bright red
        new Color(0x5AF78E), // bright green
        new Color(0xFFFF66), // bright yellow
        new Color(0x8AC7FF), // bright blue
        new Color(0xF2A2F2), // bright magenta
        new Color(0x7FE5F5), // bright cyan
        new Color(0xFFFFFF), // bright white
    };

    private static final Color[] BACKGROUNDS = {
        new Color(0x000000), // black
        new Color(0xA1260D), // red
        new Color(0x86EFAC), // green, light so that blue text from ls stays legible on world-writable directories
        new Color(0xB59A00), // yellow
        new Color(0x0F4C9C), // blue
        new Color(0x8B2F8B), // magenta
        new Color(0x1793B5), // cyan
        new Color(0xCCCCCC), // white
        new Color(0x5A5A5A), // bright black
        new Color(0xC7372F), // bright red
        new Color(0xB3F5C9), // bright green
        new Color(0xE5E510), // bright yellow
        new Color(0x2472C8), // bright blue
        new Color(0xB23FB2), // bright magenta
        new Color(0x29B8DB), // bright cyan
        new Color(0xFFFFFF), // bright white
    };

    private DarkTerminalPalette() {}

    @Override
    protected Color getForegroundByColorIndex(int colorIndex) {
        return FOREGROUNDS[colorIndex];
    }

    @Override
    protected Color getBackgroundByColorIndex(int colorIndex) {
        return BACKGROUNDS[colorIndex];
    }
}
