package com.mmlinaric.remotemanager.connection;

import javax.swing.JComponent;

public interface RemoteSession {
    void connect() throws Exception;

    void disconnect();

    boolean isConnected();

    JComponent component();
}
