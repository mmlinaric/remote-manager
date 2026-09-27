package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import java.awt.Frame;
import javax.swing.JFrame;
import javax.swing.JSplitPane;

/** Reads and writes window geometry without exposing settings keys to the UI workflow. */
final class WindowPreferences {
    private final SettingsRepository settings;

    WindowPreferences(SettingsRepository settings) {
        this.settings = settings;
    }

    int sidebarWidth() {
        int saved = number("window.divider", 320);
        return saved < 190 ? 320 : saved;
    }

    int detailsHeight() {
        return number("window.hostDetailsHeight", 0);
    }

    void restoreFrame(JFrame frame) {
        frame.setSize(number("window.width", 1120), number("window.height", 720));
        int x = number("window.x", -1);
        int y = number("window.y", -1);
        if (x >= 0 && y >= 0) {
            frame.setLocation(x, y);
        } else {
            frame.setLocationRelativeTo(null);
        }
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    }

    void saveFrame(
            JFrame frame,
            JSplitPane workspace,
            boolean sidebarInitialized,
            boolean detailsInitialized,
            int detailsHeight)
            throws Exception {
        if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != Frame.MAXIMIZED_BOTH) {
            put("window.x", Integer.toString(frame.getX()));
            put("window.y", Integer.toString(frame.getY()));
            put("window.width", Integer.toString(frame.getWidth()));
            put("window.height", Integer.toString(frame.getHeight()));
        }
        if (sidebarInitialized) {
            put("window.divider", Integer.toString(workspace.getDividerLocation()));
        }
        if (detailsInitialized) {
            put("window.hostDetailsHeight", Integer.toString(detailsHeight));
        }
    }

    void put(String key, String value) throws Exception {
        settings.put(key, value);
    }

    private int number(String key, int fallback) {
        try {
            return settings.get(key).map(Integer::parseInt).orElse(fallback);
        } catch (Exception error) {
            return fallback;
        }
    }
}
