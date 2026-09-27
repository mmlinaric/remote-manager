package com.example.remotemanager.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class UpdateServiceTest {
  @Test void comparesStableReleaseVersions() {
    assertTrue(UpdateService.isNewer("v1.2.0", "1.1.9"));
    assertFalse(UpdateService.isNewer("v1.2", "1.2.0"));
    assertFalse(UpdateService.isNewer("v1.1.9", "1.2.0"));
    assertThrows(IllegalArgumentException.class, () -> UpdateService.isNewer("v1.2-beta", "1.1.0"));
  }

  @Test void readsTheLatestReleaseResponse() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/latest", exchange -> {
      byte[] body = ("{\"tag_name\":\"v0.2.0\","
          + "\"html_url\":\"https://github.example/releases/v0.2.0\"}")
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      UpdateService service = new UpdateService(HttpClient.newHttpClient(),
          java.net.URI.create("http://localhost:" + server.getAddress().getPort() + "/latest"));
      Optional<AppRelease> release = service.findNewerRelease("0.1.0");
      assertTrue(release.isPresent());
      assertEquals("0.2.0", release.orElseThrow().version());
      assertEquals("https://github.example/releases/v0.2.0", release.orElseThrow().page().toString());
    } finally {
      server.stop(0);
    }
  }
}
