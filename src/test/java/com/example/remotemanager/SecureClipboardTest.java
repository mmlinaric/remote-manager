package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.remotemanager.util.SecureClipboard;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class SecureClipboardTest {
  @Test
  void clearsOnlyItsUnchangedValue() throws Exception {
    Clipboard clipboard = new Clipboard("test");
    try (var scheduler = Executors.newSingleThreadScheduledExecutor()) {
      SecureClipboard service = new SecureClipboard(clipboard, scheduler);
      service.copy("temporary".toCharArray(), Duration.ofMillis(30));
      Thread.sleep(100);
      assertEquals("", clipboard.getData(DataFlavor.stringFlavor));

      service.copy("temporary".toCharArray(), Duration.ofMillis(30));
      clipboard.setContents(new StringSelection("new value"), null);
      Thread.sleep(100);
      assertEquals("new value", clipboard.getData(DataFlavor.stringFlavor));
    }
  }
}
