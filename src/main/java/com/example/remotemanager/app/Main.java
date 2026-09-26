package com.example.remotemanager.app;

import com.example.remotemanager.persistence.SettingsRepository;
import com.example.remotemanager.ui.SelectionColors;
import com.example.remotemanager.ui.main.MainWindow;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.unix.X11;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main() {}

  public static void main(String[] args) {
    try {
      Files.createDirectories(AppPaths.dataDirectory());
      SettingsRepository settings = new SettingsRepository(AppPaths.dataDirectory().resolve("settings.properties"));
      SwingUtilities.invokeLater(() -> showWindow(settings));
    } catch (Exception error) {
      LOG.error("Could not initialize application settings", error);
      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  null,
                  "Could not initialize application settings: " + error.getMessage(),
                  "Startup error",
                  JOptionPane.ERROR_MESSAGE));
    }
  }

  private static void showWindow(SettingsRepository settings) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception error) {
      LOG.warn("Could not select the system look and feel", error);
    }
    UIManager.put("MenuItem.margin", new java.awt.Insets(0, 2, 0, 2));
    UIManager.put("MenuItem.checkIcon", new javax.swing.Icon() {
      @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {}
      @Override public int getIconWidth() { return 0; }
      @Override public int getIconHeight() { return 22; }
    });
    UIManager.put("MenuItem.afterCheckIconGap", 0);
    UIManager.put("MenuItem.textIconGap", 1);
    SelectionColors.configureTextInputs();
    MainWindow window = new MainWindow(settings);
    configureWindow(window);
    window.setVisible(true);
    window.checkForUpdatesAutomatically();
  }

  private static void configureWindow(MainWindow window) {
    window.setIconImage(new ImageIcon(Main.class.getResource("/icons/app/remote-manager.png")).getImage());

    if (!Platform.isLinux()) return;
    window.addNotify();
    try {
      X11 x11 = X11.INSTANCE;
      X11.Display display = x11.XOpenDisplay(null);
      if (display == null) return;
      try {
        // GNOME uses the X11 class as the app name when there is no desktop entry.
        byte[] value = "remote-manager\0Remote Manager\0".getBytes(StandardCharsets.ISO_8859_1);
        Memory data = new Memory(value.length);
        data.write(0, value, 0, value.length);
        x11.XChangeProperty(display, new X11.Window(Native.getWindowID(window)),
            x11.XInternAtom(display, "WM_CLASS", false), X11.XA_STRING, 8,
            X11.PropModeReplace, data, value.length);
        x11.XSync(display, false);
      } finally {
        x11.XCloseDisplay(display);
      }
    } catch (RuntimeException | LinkageError error) {
      LOG.warn("Could not set the Linux window app name", error);
    }
  }
}
