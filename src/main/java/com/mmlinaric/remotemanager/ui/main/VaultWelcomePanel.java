package com.mmlinaric.remotemanager.ui.main;

import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/** The locked-workspace screen and its vault lifecycle controls. */
final class VaultWelcomePanel extends JPanel {
    private final JLabel vaultPath = new JLabel("No vault selected");
    private final JButton unlockButton = new JButton("Unlock", SilkIcons.UNLOCK);
    private final JButton openButton = new JButton("Open vault...", SilkIcons.OPEN_VAULT);
    private final JButton createButton = new JButton("Create vault...", SilkIcons.CREATE_VAULT);
    private final JProgressBar creationProgress = new JProgressBar();

    VaultWelcomePanel(Runnable unlock, Runnable open, Runnable create) {
        super(new GridBagLayout());
        unlockButton.addActionListener(event -> unlock.run());
        openButton.addActionListener(event -> open.run());
        createButton.addActionListener(event -> create.run());
        add(content());
    }

    JLabel vaultPath() {
        return vaultPath;
    }

    JButton unlockButton() {
        return unlockButton;
    }

    JButton openButton() {
        return openButton;
    }

    JButton createButton() {
        return createButton;
    }

    JProgressBar creationProgress() {
        return creationProgress;
    }

    private JPanel content() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("KeePass vault"), BorderFactory.createEmptyBorder(16, 22, 20, 22)));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 14, 5);
        content.add(new JLabel("Unlock your vault to view hosts and identities."), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(4, 5, 14, 5);
        content.add(vaultPath, constraints);
        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.insets = new Insets(4, 5, 4, 5);
        content.add(unlockButton, constraints);
        constraints.gridx++;
        content.add(openButton, constraints);
        constraints.gridx++;
        content.add(createButton, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        creationProgress.setIndeterminate(true);
        creationProgress.setStringPainted(true);
        creationProgress.setString("Creating vault...");
        creationProgress.setVisible(false);
        content.add(creationProgress, constraints);
        return content;
    }
}
