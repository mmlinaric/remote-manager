package com.example.remotemanager.ui.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.AWTEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FontPickerTest {
  @Test
  void clickingOpenPickerClosesPopupUntilNextClick() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    SwingUtilities.invokeAndWait(() -> {
      JFrame frame = new JFrame();
      FontPicker picker = new FontPicker(List.of("Monospaced", "Serif"), "Monospaced");
      frame.add(picker);
      frame.pack();
      try {
        frame.setVisible(true);
        JPopupMenu popup = popupOf(picker);
        picker.doClick();
        assertTrue(popup.isVisible());

        click(picker);
        assertFalse(popup.isVisible());

        click(picker);
        assertTrue(popup.isVisible());
      } finally {
        frame.dispose();
      }
    });
  }

  @Test
  void doesNotReopenWhenPopupDismissesBeforeButtonHandlesClick() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    JFrame[] frame = new JFrame[1];
    FontPicker[] picker = new FontPicker[1];
    JPopupMenu[] popup = new JPopupMenu[1];
    SwingUtilities.invokeAndWait(() -> {
      frame[0] = new JFrame();
      picker[0] = new FontPicker(List.of("Monospaced", "Serif"), "Monospaced");
      frame[0].add(picker[0]);
      frame[0].pack();
      frame[0].setVisible(true);
      popup[0] = popupOf(picker[0]);
      picker[0].doClick();
      assertTrue(popup[0].isVisible());
    });
    AWTEventListener dismissBeforeButton = event -> {
      if (event instanceof MouseEvent mouse && mouse.getSource() == picker[0]
          && mouse.getID() == MouseEvent.MOUSE_PRESSED && popup[0].isVisible())
        popup[0].setVisible(false);
    };
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    toolkit.addAWTEventListener(dismissBeforeButton, AWTEvent.MOUSE_EVENT_MASK);
    try {
      Point screen = picker[0].getLocationOnScreen();
      int x = picker[0].getWidth() / 2;
      int y = picker[0].getHeight() / 2;
      long when = System.currentTimeMillis();
      toolkit.getSystemEventQueue().postEvent(new MouseEvent(picker[0],
          MouseEvent.MOUSE_PRESSED, when, MouseEvent.BUTTON1_DOWN_MASK,
          x, y, screen.x + x, screen.y + y, 1, false, MouseEvent.BUTTON1));
      toolkit.getSystemEventQueue().postEvent(new MouseEvent(picker[0],
          MouseEvent.MOUSE_RELEASED, when + 1, 0,
          x, y, screen.x + x, screen.y + y, 1, false, MouseEvent.BUTTON1));
      SwingUtilities.invokeAndWait(() -> assertFalse(popup[0].isVisible()));
    } finally {
      toolkit.removeAWTEventListener(dismissBeforeButton);
      SwingUtilities.invokeAndWait(frame[0]::dispose);
    }
  }

  private static JPopupMenu popupOf(FontPicker picker) {
    try {
      Field field = FontPicker.class.getDeclaredField("popup");
      field.setAccessible(true);
      return (JPopupMenu) field.get(picker);
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
  }

  private static void click(FontPicker picker) {
    int x = picker.getWidth() / 2;
    int y = picker.getHeight() / 2;
    Point screen = picker.getLocationOnScreen();
    long when = System.currentTimeMillis();
    picker.dispatchEvent(new MouseEvent(picker, MouseEvent.MOUSE_PRESSED, when,
        MouseEvent.BUTTON1_DOWN_MASK, x, y, screen.x + x, screen.y + y,
        1, false, MouseEvent.BUTTON1));
    picker.dispatchEvent(new MouseEvent(picker, MouseEvent.MOUSE_RELEASED, when + 1,
        0, x, y, screen.x + x, screen.y + y,
        1, false, MouseEvent.BUTTON1));
  }
}
