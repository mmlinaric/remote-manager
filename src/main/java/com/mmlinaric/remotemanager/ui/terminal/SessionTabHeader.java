package com.mmlinaric.remotemanager.ui.terminal;

import com.mmlinaric.remotemanager.ui.SilkIcons;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Displays one session title and its accessible close action in the tab strip. */
final class SessionTabHeader extends JPanel {
    private final JLabel label;

    SessionTabHeader(String title, Icon icon, Runnable close, String connectionName) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setOpaque(false);
        label = new JLabel(title, icon, JLabel.LEADING);
        add(label);
        JButton closeButton = new JButton(SilkIcons.CLOSE);
        closeButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.setToolTipText("Close this session");
        closeButton.getAccessibleContext().setAccessibleName("Close " + connectionName + " session");
        closeButton.addActionListener(event -> close.run());
        add(closeButton);
    }

    void update(String title, Icon icon) {
        label.setText(title);
        label.setIcon(icon);
    }
}
