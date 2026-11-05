package com.mmlinaric.remotemanager.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SshRemoteSessionEndTest {
    @Test
    void reportsTheEndWhenTheShellReachesEndOfInput() throws Exception {
        assertSessionEndReported(new FinishedConnector(null));
    }

    @Test
    void reportsTheEndWhenReadingTheShellFails() throws Exception {
        assertSessionEndReported(new FinishedConnector(new IOException("Connection lost")));
    }

    private static void assertSessionEndReported(TtyConnector connector) throws Exception {
        CountDownLatch ended = new CountDownLatch(1);
        SshRemoteSession session =
                new SshRemoteSession(null, null, Path.of("known_hosts"), null, null, "Monospaced", 13, 100, true);
        session.onSessionEnded(ended::countDown);
        JediTermWidget terminal = (JediTermWidget) session.component();
        try {
            SwingUtilities.invokeAndWait(() -> {
                terminal.setTtyConnector(connector);
                terminal.start();
            });

            assertTrue(ended.await(10, TimeUnit.SECONDS), "The session end was not reported");
            assertFalse(session.isConnected());
        } finally {
            SwingUtilities.invokeAndWait(terminal::close);
        }
    }

    /** A shell that has already finished, either at end of input or with a read error. */
    private static final class FinishedConnector implements TtyConnector {
        private final IOException failure;

        private FinishedConnector(IOException failure) {
            this.failure = failure;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (failure != null) throw failure;
            return -1;
        }

        @Override
        public void write(byte[] bytes) {}

        @Override
        public void write(String text) {}

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public void resize(TermSize size) {}

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean ready() {
            return false;
        }

        @Override
        public String getName() {
            return "Finished";
        }

        @Override
        public void close() {}
    }
}
