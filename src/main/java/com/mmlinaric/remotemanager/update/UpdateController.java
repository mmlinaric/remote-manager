package com.mmlinaric.remotemanager.update;

import com.mmlinaric.remotemanager.app.AppVersion;
import com.mmlinaric.remotemanager.persistence.SettingsRepository;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates background release checks and their small, dismissible UI. */
public final class UpdateController implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UpdateController.class);
  private static final Duration AUTO_CHECK_INTERVAL = Duration.ofDays(1);
  private static final String LAST_CHECK = "updates.lastCheckEpochMillis";
  private static final String DISMISSED = "updates.dismissedVersion";

  private final Component parent;
  private final SettingsRepository settings;
  private final UpdateService service;
  private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "remote-manager-update-check");
    thread.setDaemon(true);
    return thread;
  });
  private final AtomicBoolean checking = new AtomicBoolean();
  private final JPanel banner = new JPanel(new BorderLayout(12, 0));
  private final JLabel message = new JLabel();
  private final JButton releaseButton = new JButton("View release");
  private AppRelease visibleRelease;

  public UpdateController(Component parent, SettingsRepository settings) {
    this(parent, settings, new UpdateService());
  }

  UpdateController(Component parent, SettingsRepository settings, UpdateService service) {
    this.parent = parent;
    this.settings = settings;
    this.service = service;
    banner.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    banner.add(message, BorderLayout.CENTER);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    releaseButton.addActionListener(event -> openVisibleRelease());
    actions.add(releaseButton);
    JButton dismiss = new JButton("Dismiss");
    dismiss.addActionListener(event -> dismissVisibleRelease());
    actions.add(dismiss);
    banner.add(actions, BorderLayout.EAST);
    banner.setVisible(false);
  }

  public JPanel banner() {
    return banner;
  }

  public void checkAutomatically() {
    Optional<String> current = AppVersion.packaged();
    if (current.isEmpty() || checkedRecently()) return;
    check(current.get(), false);
  }

  public void checkManually() {
    Optional<String> current = AppVersion.packaged();
    if (current.isEmpty()) {
      JOptionPane.showMessageDialog(parent,
          "Update checks are available in packaged release builds.\nCurrent version: " + AppVersion.display(),
          "Check for updates", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    check(current.get(), true);
  }

  private void check(String currentVersion, boolean manual) {
    if (!checking.compareAndSet(false, true)) {
      if (manual) JOptionPane.showMessageDialog(parent, "An update check is already running.",
          "Check for updates", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    CompletableFuture.supplyAsync(() -> {
      try {
        return service.findNewerRelease(currentVersion);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(interrupted);
      } catch (IOException error) {
        throw new RuntimeException(error);
      } finally {
        saveLastCheck();
      }
    }, worker).whenComplete((release, error) -> SwingUtilities.invokeLater(() -> {
      checking.set(false);
      if (!parent.isShowing()) return;
      if (error != null) {
        LOG.warn("Could not check for application updates", error);
        if (manual) showError(error);
      } else if (release.isPresent()) {
        showRelease(release.get(), manual);
      } else if (manual) {
        JOptionPane.showMessageDialog(parent,
            "Remote Manager " + currentVersion + " is up to date.",
            "Check for updates", JOptionPane.INFORMATION_MESSAGE);
      }
    }));
  }

  private boolean checkedRecently() {
    try {
      long previous = settings.get(LAST_CHECK).map(Long::parseLong).orElse(0L);
      return previous > 0 && Duration.between(Instant.ofEpochMilli(previous), Instant.now())
          .compareTo(AUTO_CHECK_INTERVAL) < 0;
    } catch (RuntimeException error) {
      return false;
    }
  }

  private void saveLastCheck() {
    try {
      settings.put(LAST_CHECK, Long.toString(System.currentTimeMillis()));
    } catch (IOException error) {
      LOG.warn("Could not save the last update check time", error);
    }
  }

  private void showRelease(AppRelease release, boolean manual) {
    if (!manual && settings.get(DISMISSED).filter(release.version()::equals).isPresent()) return;
    visibleRelease = release;
    message.setText("Remote Manager " + release.version() + " is available.");
    banner.setVisible(true);
    banner.revalidate();
  }

  private void dismissVisibleRelease() {
    if (visibleRelease != null) {
      try {
        settings.put(DISMISSED, visibleRelease.version());
      } catch (IOException error) {
        LOG.warn("Could not save the dismissed update version", error);
      }
    }
    banner.setVisible(false);
  }

  private void openVisibleRelease() {
    if (visibleRelease == null) return;
    try {
      if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
        throw new IOException("Opening web links is not supported on this system");
      Desktop.getDesktop().browse(visibleRelease.page());
    } catch (Exception error) {
      JOptionPane.showMessageDialog(parent,
          "Could not open the release page: " + error.getMessage() + "\n" + visibleRelease.page(),
          "Remote Manager", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void showError(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null) cause = cause.getCause();
    JOptionPane.showMessageDialog(parent,
        "Could not check for updates: " + cause.getMessage(),
        "Check for updates", JOptionPane.ERROR_MESSAGE);
  }

  @Override public void close() {
    worker.shutdownNow();
  }
}
