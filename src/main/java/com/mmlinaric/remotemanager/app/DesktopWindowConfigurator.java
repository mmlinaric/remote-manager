package com.mmlinaric.remotemanager.app;

import com.mmlinaric.remotemanager.ui.main.MainWindow;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.unix.X11;
import java.nio.charset.StandardCharsets;
import javax.swing.ImageIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies operating-system-specific window integration after the frame is created. */
final class DesktopWindowConfigurator {
    private static final Logger LOG = LoggerFactory.getLogger(DesktopWindowConfigurator.class);

    private DesktopWindowConfigurator() {}

    static void configure(MainWindow window) {
        window.setIconImage(
                new ImageIcon(DesktopWindowConfigurator.class.getResource("/icons/app/remote-manager.png")).getImage());
        if (Platform.isLinux()) {
            configureLinuxApplicationClass(window);
        }
    }

    private static void configureLinuxApplicationClass(MainWindow window) {
        window.addNotify();
        try {
            X11 x11 = X11.INSTANCE;
            X11.Display display = x11.XOpenDisplay(null);
            if (display == null) {
                return;
            }
            try {
                // GNOME uses this X11 class as the app name when no desktop entry is present.
                byte[] value = "remote-manager\0Remote Manager\0".getBytes(StandardCharsets.ISO_8859_1);
                Memory data = new Memory(value.length);
                data.write(0, value, 0, value.length);
                x11.XChangeProperty(
                        display,
                        new X11.Window(Native.getWindowID(window)),
                        x11.XInternAtom(display, "WM_CLASS", false),
                        X11.XA_STRING,
                        8,
                        X11.PropModeReplace,
                        data,
                        value.length);
                x11.XSync(display, false);
            } finally {
                x11.XCloseDisplay(display);
            }
        } catch (RuntimeException | LinkageError error) {
            LOG.warn("Could not set the Linux window app name", error);
        }
    }
}
