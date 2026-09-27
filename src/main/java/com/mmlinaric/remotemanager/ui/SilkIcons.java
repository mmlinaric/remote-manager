package com.mmlinaric.remotemanager.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/** Icons bundled from the FamFamFam Silk 1.3 set. */
public final class SilkIcons {
  public static final Icon EDIT = load("application_form_edit");
  public static final Icon RECONNECT = load("arrow_refresh");
  public static final Icon CREATE_VAULT = load("database_add");
  public static final Icon CONNECTED = load("bullet_green");
  public static final Icon FAILED = load("bullet_red");
  public static final Icon DISCONNECTED = load("bullet_orange");
  public static final Icon CONNECTING = load("bullet_yellow");
  public static final Icon SETTINGS = load("cog");
  public static final Icon ROOT = load("computer");
  public static final Icon NEW_CONNECTION = load("computer_add");
  public static final Icon CONNECT = load("connect");
  public static final Icon CLOSE = load("cross");
  public static final Icon DELETE = load("delete");
  public static final Icon DISCONNECT = load("disconnect");
  public static final Icon SAVE = load("disk");
  public static final Icon EXIT = load("door_out");
  public static final Icon FOLDER = load("folder");
  public static final Icon NEW_FOLDER = load("folder_add");
  public static final Icon OPEN_VAULT = load("folder_key");
  public static final Icon FONT = load("font");
  public static final Icon FONT_INCREASE = load("font_add");
  public static final Icon FONT_DECREASE = load("font_delete");
  public static final Icon ABOUT = load("information");
  public static final Icon VAULT = load("key");
  public static final Icon NEW_IDENTITY = load("key_add");
  public static final Icon COPY_PASSWORD = load("key_go");
  public static final Icon LOCK = load("lock");
  public static final Icon UNLOCK = load("lock_open");
  public static final Icon CONNECTION = load("server");

  private SilkIcons() {}

  private static Icon load(String name) {
    URL resource = SilkIcons.class.getResource("/icons/silk/" + name + ".png");
    if (resource == null) {
      throw new IllegalStateException("Missing Silk icon: " + name);
    }
    try {
      BufferedImage original = ImageIO.read(resource);
      if (original == null) {
        throw new IllegalStateException("Could not decode Silk icon: " + name);
      }
      var font = UIManager.getFont("MenuItem.font");
      int size = font == null ? 16 : Math.max(16, Math.round(font.getSize() / 8f) * 8);
      if (size == 16) {
        return new ImageIcon(original);
      }
      BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = scaled.createGraphics();
      try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(original, 0, 0, size, size, null);
      } finally {
        graphics.dispose();
      }
      return new ImageIcon(scaled);
    } catch (IOException error) {
      throw new IllegalStateException("Could not load Silk icon: " + name, error);
    }
  }
}
