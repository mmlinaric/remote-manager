package com.example.remotemanager.persistence;

import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.model.Connection;
import com.example.remotemanager.model.ConnectionFolder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConnectionRepository {
    private final Database database;
    public ConnectionRepository(Database database) { this.database = database; }

    public List<ConnectionFolder> folders() throws SQLException {
        try (var db = database.open(); var query = db.prepareStatement("SELECT * FROM connection_folder ORDER BY sort_order, name");
             var rows = query.executeQuery()) {
            List<ConnectionFolder> result = new ArrayList<>();
            while (rows.next()) result.add(new ConnectionFolder(UUID.fromString(rows.getString("id")), uuid(rows, "parent_folder_id"), rows.getString("name"), rows.getInt("sort_order")));
            return result;
        }
    }

    public List<Connection> connections() throws SQLException {
        try (var db = database.open(); var query = db.prepareStatement("SELECT * FROM connection ORDER BY sort_order, name");
             var rows = query.executeQuery()) {
            List<Connection> result = new ArrayList<>();
            while (rows.next()) result.add(new Connection(UUID.fromString(rows.getString("id")), rows.getString("name"), rows.getString("hostname"),
                    rows.getInt("port"), rows.getString("username"), uuid(rows, "parent_folder_id"),
                    AuthenticationType.valueOf(rows.getString("authentication_type")), uuid(rows, "ssh_credential_entry_id"),
                    uuid(rows, "sudo_credential_entry_id"), rows.getString("private_key_attachment_name"),
                    rows.getString("private_key_file_path"), rows.getString("notes"), rows.getInt("sort_order")));
            return result;
        }
    }

    public void save(Connection value) throws SQLException {
        String sql = "INSERT INTO connection(id,parent_folder_id,name,hostname,port,username,authentication_type,ssh_credential_entry_id,sudo_credential_entry_id,private_key_attachment_name,private_key_file_path,notes,sort_order) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET parent_folder_id=excluded.parent_folder_id,name=excluded.name,hostname=excluded.hostname,port=excluded.port,username=excluded.username,authentication_type=excluded.authentication_type,ssh_credential_entry_id=excluded.ssh_credential_entry_id,sudo_credential_entry_id=excluded.sudo_credential_entry_id,private_key_attachment_name=excluded.private_key_attachment_name,private_key_file_path=excluded.private_key_file_path,notes=excluded.notes,sort_order=excluded.sort_order";
        try (var db = database.open(); var query = db.prepareStatement(sql)) {
            query.setString(1, value.id().toString()); setUuid(query, 2, value.parentFolderId()); query.setString(3, value.name());
            query.setString(4, value.hostname()); query.setInt(5, value.port()); query.setString(6, value.username());
            query.setString(7, value.authenticationType().name()); setUuid(query, 8, value.sshCredentialEntryId());
            setUuid(query, 9, value.sudoCredentialEntryId()); query.setString(10, value.privateKeyAttachmentName());
            query.setString(11, value.privateKeyFilePath()); query.setString(12, value.notes()); query.setInt(13, value.sortOrder());
            query.executeUpdate();
        }
    }

    public void save(ConnectionFolder value) throws SQLException {
        try (var db = database.open(); var query = db.prepareStatement("INSERT INTO connection_folder(id,parent_folder_id,name,sort_order) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET parent_folder_id=excluded.parent_folder_id,name=excluded.name,sort_order=excluded.sort_order")) {
            query.setString(1, value.id().toString()); setUuid(query, 2, value.parentFolderId()); query.setString(3, value.name()); query.setInt(4, value.sortOrder()); query.executeUpdate();
        }
    }

    public void deleteConnection(UUID id) throws SQLException { delete("DELETE FROM connection WHERE id=?", id); }
    public void deleteFolder(UUID id) throws SQLException { delete("DELETE FROM connection_folder WHERE id=?", id); }

    private void delete(String sql, UUID id) throws SQLException {
        try (var db = database.open(); var query = db.prepareStatement(sql)) { query.setString(1, id.toString()); query.executeUpdate(); }
    }

    private static UUID uuid(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column); return value == null ? null : UUID.fromString(value);
    }
    private static void setUuid(PreparedStatement query, int index, UUID value) throws SQLException {
        query.setString(index, value == null ? null : value.toString());
    }
}
