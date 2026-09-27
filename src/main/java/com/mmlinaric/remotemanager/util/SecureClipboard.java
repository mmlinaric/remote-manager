package com.mmlinaric.remotemanager.util;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Copies a secret for a bounded interval and clears it only when the clipboard still contains that value. */
public final class SecureClipboard {
    private final Clipboard clipboard;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingCleanup;

    public SecureClipboard(Clipboard clipboard, ScheduledExecutorService scheduler) {
        this.clipboard = clipboard;
        this.scheduler = scheduler;
    }

    public synchronized void copy(char[] secret, Duration timeout) {
        if (pendingCleanup != null) {
            pendingCleanup.cancel(false);
        }

        String value = new String(secret);
        StringSelection selection = new StringSelection(value);
        clipboard.setContents(selection, null);
        pendingCleanup = scheduler.schedule(() -> clearIfUnchanged(value), timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private synchronized void clearIfUnchanged(String value) {
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String current = (String) contents.getTransferData(DataFlavor.stringFlavor);
                if (value.equals(current)) {
                    clipboard.setContents(new StringSelection(""), null);
                }
            }
        } catch (Exception ignored) {
            // Clipboard access may be temporarily unavailable on another process.
        } finally {
            pendingCleanup = null;
        }
    }
}
