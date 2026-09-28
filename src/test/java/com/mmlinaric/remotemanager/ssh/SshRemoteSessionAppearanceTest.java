package com.mmlinaric.remotemanager.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jediterm.terminal.ui.JediTermWidget;
import java.awt.Color;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SshRemoteSessionAppearanceTest {
    @Test
    void updatesTheTerminalCanvasWhenAppearanceChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SshRemoteSession session = new SshRemoteSession(
                    null, null, Path.of("known_hosts"), null, null, "Monospaced", 13, 100, true);
            JediTermWidget terminal = (JediTermWidget) session.component();
            try {
                assertEquals(new Color(0x1E, 0x1E, 0x1E), terminal.getTerminalPanel().getBackground());
                assertEquals(new Color(0xD4, 0xD4, 0xD4), terminal.getTerminalPanel().getForeground());

                session.setDarkAppearance(false);

                assertEquals(Color.WHITE, terminal.getTerminalPanel().getBackground());
                assertEquals(Color.BLACK, terminal.getTerminalPanel().getForeground());
            } finally {
                terminal.close();
            }
        });
    }
}
