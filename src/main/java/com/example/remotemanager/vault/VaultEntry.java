package com.example.remotemanager.vault;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VaultEntry(
    UUID id, String title, String username, Map<String, String> fields, List<String> attachments) {
  @Override
  public String toString() {
    return title == null || title.isBlank() ? id.toString() : title;
  }
}
