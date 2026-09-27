package com.mmlinaric.remotemanager.ui.main;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Tracks activity in one window hierarchy and triggers a configured vault auto-lock. */
final class AutoLockMonitor implements AutoCloseable {
    private final Window root;
    private final BooleanSupplier isLocked;
    private final IntSupplier autoLockMinutes;
    private final Runnable lock;
    private final AWTEventListener activityListener = this::recordActivity;
    private final Timer timer = new Timer(15_000, event -> lockIfInactive());
    private long lastActivity = System.nanoTime();

    AutoLockMonitor(Window root, BooleanSupplier isLocked, IntSupplier autoLockMinutes, Runnable lock) {
        this.root = root;
        this.isLocked = isLocked;
        this.autoLockMinutes = autoLockMinutes;
        this.lock = lock;
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(
                        activityListener,
                        AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        timer.start();
    }

    void reset() {
        lastActivity = System.nanoTime();
    }

    @Override
    public void close() {
        timer.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
    }

    private void recordActivity(AWTEvent event) {
        if (isLocked.getAsBoolean() || !(event.getSource() instanceof Component component)) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(component);
        while (owner != null) {
            if (owner == root) {
                reset();
                return;
            }
            owner = owner.getOwner();
        }
    }

    private void lockIfInactive() {
        int minutes = autoLockMinutes.getAsInt();
        if (!isLocked.getAsBoolean()
                && minutes > 0
                && System.nanoTime() - lastActivity >= TimeUnit.MINUTES.toNanos(minutes)) {
            lock.run();
        }
    }
}
