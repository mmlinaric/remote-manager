package com.mmlinaric.remotemanager.connection;

import javax.swing.JComponent;

/** A connected remote endpoint that can provide the terminal component displayed by the desktop UI. */
public interface RemoteSession {
    void connect() throws Exception;

    void disconnect();

    boolean isConnected();

    JComponent component();
}
