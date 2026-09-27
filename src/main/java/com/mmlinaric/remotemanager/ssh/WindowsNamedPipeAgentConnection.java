package com.mmlinaric.remotemanager.ssh;

import com.hierynomus.sshj.userauth.agent.AgentConnection;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class WindowsNamedPipeAgentConnection implements AgentConnection {
  private static final String AGENT_PIPE = "\\\\.\\pipe\\openssh-ssh-agent";
  private static final int ERROR_MORE_DATA = 234;

  private final WinNT.HANDLE handle;
  private boolean closed;

  public WindowsNamedPipeAgentConnection() throws IOException {
    handle =
        Kernel32.INSTANCE.CreateFile(
            AGENT_PIPE,
            WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
            0,
            null,
            WinNT.OPEN_EXISTING,
            0,
            null);
    if (WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
      throw new IOException(
          "Windows OpenSSH agent is unavailable (error " + Kernel32.INSTANCE.GetLastError() + ")");
    }
  }

  @Override
  public InputStream getInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
      }

      @Override
      public int read(byte[] data, int offset, int length) throws IOException {
        if (closed) {
          return -1;
        }
        if (length == 0) {
          return 0;
        }
        byte[] buffer = new byte[length];
        IntByReference count = new IntByReference();
        boolean success = Kernel32.INSTANCE.ReadFile(handle, buffer, length, count, null);
        if (!success && Kernel32.INSTANCE.GetLastError() != ERROR_MORE_DATA) {
          throw new IOException("Could not read from Windows SSH agent pipe");
        }
        if (count.getValue() == 0) {
          return -1;
        }
        System.arraycopy(buffer, 0, data, offset, count.getValue());
        return count.getValue();
      }
    };
  }

  @Override
  public OutputStream getOutputStream() {
    return new OutputStream() {
      @Override
      public void write(int value) throws IOException {
        write(new byte[] {(byte) value});
      }

      @Override
      public void write(byte[] data, int offset, int length) throws IOException {
        if (closed) {
          throw new IOException("Windows SSH agent pipe is closed");
        }
        byte[] buffer = java.util.Arrays.copyOfRange(data, offset, offset + length);
        IntByReference count = new IntByReference();
        if (!Kernel32.INSTANCE.WriteFile(handle, buffer, buffer.length, count, null)
            || count.getValue() != buffer.length) {
          throw new IOException("Could not write to Windows SSH agent pipe");
        }
      }
    };
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      closed = true;
      if (!Kernel32.INSTANCE.CloseHandle(handle)) {
        throw new IOException("Could not close Windows SSH agent pipe");
      }
    }
  }
}
