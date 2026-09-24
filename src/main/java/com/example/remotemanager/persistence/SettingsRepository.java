package com.example.remotemanager.persistence;

import java.sql.SQLException;
import java.util.Optional;

public final class SettingsRepository {
  private final Database database;

  public SettingsRepository(Database database) {
    this.database = database;
  }

  public Optional<String> get(String key) throws SQLException {
    try (var db = database.open();
        var query = db.prepareStatement("SELECT value FROM application_setting WHERE key=?")) {
      query.setString(1, key);
      try (var rows = query.executeQuery()) {
        return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty();
      }
    }
  }

  public void put(String key, String value) throws SQLException {
    try (var db = database.open();
        var query =
            db.prepareStatement(
                "INSERT INTO application_setting(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE"
                    + " SET value=excluded.value")) {
      query.setString(1, key);
      query.setString(2, value);
      query.executeUpdate();
    }
  }
}
