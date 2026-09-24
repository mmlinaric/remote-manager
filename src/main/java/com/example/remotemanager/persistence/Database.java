package com.example.remotemanager.persistence;

import org.flywaydb.core.Flyway;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private final String url;

    public Database(Path file) { url = "jdbc:sqlite:" + file.toAbsolutePath(); }

    public void migrate() {
        Flyway.configure().dataSource(url, null, null).load().migrate();
    }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }
}
