package com.mmlinaric.remotemanager.ui;

import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native file chooser for SSH keys, starting inside the usually hidden ~/.ssh directory. */
public final class KeyFilePicker {
    private KeyFilePicker() {}

    public static Path parsePath(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\"))
            return Path.of(System.getProperty("user.home"), trimmed.substring(2));
        return Path.of(trimmed);
    }

    public static Path choose(Window owner, Path previous) {
        FileDialog dialog = owner instanceof Dialog parent
                ? new FileDialog(parent, "Choose private key file", FileDialog.LOAD)
                : new FileDialog(
                        owner instanceof Frame frame ? frame : null, "Choose private key file", FileDialog.LOAD);
        Path ssh = Path.of(System.getProperty("user.home"), ".ssh");
        Path preferred = previous == null ? null : previous.toAbsolutePath().getParent();
        Path directory = preferred != null && Files.isDirectory(preferred)
                ? preferred
                : Files.isDirectory(ssh) ? ssh : Path.of(System.getProperty("user.home"));
        dialog.setDirectory(directory.toString());
        if (previous != null) dialog.setFile(previous.getFileName().toString());
        try {
            dialog.setVisible(true);
            return dialog.getFiles().length == 0 ? null : dialog.getFiles()[0].toPath();
        } finally {
            dialog.dispose();
        }
    }
}
