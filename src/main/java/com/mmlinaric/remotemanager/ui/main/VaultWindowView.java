package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.vault.WorkspaceVault;
import java.awt.CardLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Presents vault lifecycle state without making vault or session decisions. */
final class VaultWindowView {
    private final JPanel cards;
    private final JLabel vaultPath;
    private final JLabel status;
    private final JLabel vaultState;

    VaultWindowView(JPanel cards, JLabel vaultPath, JLabel status, JLabel vaultState) {
        this.cards = cards;
        this.vaultPath = vaultPath;
        this.status = status;
        this.vaultState = vaultState;
    }

    void showLocked(WorkspaceVault vault) {
        vaultPath.setText(vault == null ? "No vault selected" : vault.path().toString());
        ((CardLayout) cards.getLayout()).show(cards, "locked");
        status.setText(vault == null ? "Create or open a KeePass vault to begin." : "Vault locked");
    }

    void showWorkspace(WorkspaceVault vault) {
        ((CardLayout) cards.getLayout()).show(cards, "workspace");
        status.setText("Vault unlocked: " + vault.path());
    }

    void showSaving() {
        status.setText("Saving vault...");
    }

    void showStatus(String text) {
        status.setText(text);
    }

    void updateState(WorkspaceVault vault, boolean locked, int sessionCount, boolean saving) {
        String vaultStatus = locked ? "Locked" : "Unlocked: " + vault.path().getFileName();
        String sessionStatus = sessionCount + " session" + (sessionCount == 1 ? "" : "s");
        vaultState.setText(vaultStatus + "  |  " + sessionStatus + (saving ? "  |  Saving..." : ""));
    }
}
