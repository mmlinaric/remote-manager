package com.mmlinaric.remotemanager.app;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

/** Reserves menu alignment space without drawing a checkbox. */
final class EmptyMenuCheckIcon implements Icon {
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        // Intentionally empty: menu items reserve alignment space but have no checkmark.
    }

    @Override
    public int getIconWidth() {
        return 0;
    }

    @Override
    public int getIconHeight() {
        return 22;
    }
}
