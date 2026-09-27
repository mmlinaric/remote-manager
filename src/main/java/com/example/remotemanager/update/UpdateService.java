package com.example.remotemanager.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Looks up the latest stable GitHub release without downloading or installing anything. */
public final class UpdateService {
  static final URI LATEST_RELEASE =
      URI.create("https://api.github.com/repos/mmlinaric/remote-manager/releases/latest");
  private final HttpClient client;
  private final URI endpoint;
  private final ObjectMapper json = new ObjectMapper();

  public UpdateService() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), LATEST_RELEASE);
  }

  UpdateService(HttpClient client, URI endpoint) {
    this.client = client;
    this.endpoint = endpoint;
  }

  public Optional<AppRelease> findNewerRelease(String currentVersion)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(15))
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "Remote-Manager/" + currentVersion)
        .GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200)
      throw new IOException("GitHub returned HTTP " + response.statusCode());
    JsonNode body = json.readTree(response.body());
    String tag = requiredText(body, "tag_name");
    URI page = URI.create(requiredText(body, "html_url"));
    return isNewer(tag, currentVersion)
        ? Optional.of(new AppRelease(stripPrefix(tag), page))
        : Optional.empty();
  }

  static boolean isNewer(String candidate, String current) {
    return ReleaseVersion.parse(candidate).compareTo(ReleaseVersion.parse(current)) > 0;
  }

  private static String requiredText(JsonNode body, String name) throws IOException {
    JsonNode value = body.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank())
      throw new IOException("GitHub response did not contain " + name);
    return value.textValue();
  }

  private static String stripPrefix(String version) {
    return version.startsWith("v") || version.startsWith("V") ? version.substring(1) : version;
  }
}
