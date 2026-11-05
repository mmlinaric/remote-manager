package com.mmlinaric.remotemanager.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.mmlinaric.remotemanager.ui.terminal.DarkTerminalPalette;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SshRemoteSessionAppearanceTest {
    @Test
    void updatesTheTerminalCanvasWhenAppearanceChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SshRemoteSession session =
                    new SshRemoteSession(null, null, Path.of("known_hosts"), null, null, "Monospaced", 13, 100, true);
            JediTermWidget terminal = (JediTermWidget) session.component();
            try {
                assertEquals(
                        new Color(0x1E, 0x1E, 0x1E), terminal.getTerminalPanel().getBackground());
                assertEquals(
                        new Color(0xD4, 0xD4, 0xD4), terminal.getTerminalPanel().getForeground());

                session.setDarkAppearance(false);

                assertEquals(Color.WHITE, terminal.getTerminalPanel().getBackground());
                assertEquals(Color.BLACK, terminal.getTerminalPanel().getForeground());
            } finally {
                terminal.close();
            }
        });
    }

    @Test
    void paintsAnsiColorsFromThePaletteOfTheCurrentAppearance() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SshRemoteSession session =
                    new SshRemoteSession(null, null, Path.of("known_hosts"), null, null, "Monospaced", 13, 100, true);
            JediTermWidget terminal = (JediTermWidget) session.component();
            try {
                TerminalColor blue = new TerminalColor(4);
                // Reverse video fills the cell with the foreground color, so the check does not depend on glyph edges.
                terminal.getTerminal()
                        .characterAttributes(new TextStyle.Builder()
                                .setForeground(blue)
                                .setOption(TextStyle.Option.INVERSE, true)
                                .build());
                terminal.getTerminal().writeCharacters("XXXX");

                assertTrue(paints(terminal, DarkTerminalPalette.INSTANCE.getForeground(blue)));

                session.setDarkAppearance(false);

                assertTrue(paints(
                        terminal,
                        new DefaultSettingsProvider().getTerminalColorPalette().getForeground(blue)));
            } finally {
                terminal.close();
            }
        });
    }

    private static boolean paints(JediTermWidget terminal, com.jediterm.core.Color color) {
        JComponent panel = terminal.getTerminalPanel();
        panel.setSize(400, 100);
        BufferedImage image = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        int expected = color.getRGB() & 0xFFFFFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == expected) return true;
            }
        }
        return false;
    }
}
