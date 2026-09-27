package com.mmlinaric.remotemanager.ui.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import net.schmizz.sshj.connection.channel.direct.Session;

public final class SshTtyConnector implements TtyConnector {
    private final Session.Shell shell;
    private final InputStreamReader reader;
    private final OutputStream writer;

    public SshTtyConnector(Session.Shell shell) {
        this.shell = shell;
        reader = new InputStreamReader(shell.getInputStream(), StandardCharsets.UTF_8);
        writer = shell.getOutputStream();
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        return reader.read(buffer, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        writer.write(bytes);
        writer.flush();
    }

    @Override
    public void write(String text) throws IOException {
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return shell.isOpen();
    }

    @Override
    public void resize(TermSize size) {
        try {
            shell.changeWindowDimensions(size.getColumns(), size.getRows(), 0, 0);
        } catch (Exception error) {
            // The shell may already have closed while the component was resizing.
        }
    }

    @Override
    public int waitFor() throws InterruptedException {
        try {
            shell.join();
            return 0;
        } catch (Exception error) {
            return 1;
        }
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "SSH";
    }

    @Override
    public void close() {
        try {
            shell.close();
        } catch (IOException ignored) {
            // Closing an already closed SSH shell needs no further action.
        }
    }
}
