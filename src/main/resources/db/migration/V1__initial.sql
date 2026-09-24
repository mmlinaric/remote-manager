CREATE TABLE connection_folder (
    id TEXT PRIMARY KEY,
    parent_folder_id TEXT REFERENCES connection_folder(id),
    name TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE connection (
    id TEXT PRIMARY KEY,
    parent_folder_id TEXT REFERENCES connection_folder(id),
    name TEXT NOT NULL,
    hostname TEXT NOT NULL,
    port INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
    username TEXT NOT NULL,
    authentication_type TEXT NOT NULL CHECK (authentication_type IN ('KDBX_PRIVATE_KEY', 'SSH_AGENT', 'PRIVATE_KEY_FILE', 'PASSWORD')),
    ssh_credential_entry_id TEXT,
    sudo_credential_entry_id TEXT,
    private_key_attachment_name TEXT,
    private_key_file_path TEXT,
    notes TEXT NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX connection_folder_parent_idx ON connection_folder(parent_folder_id, sort_order);
CREATE INDEX connection_parent_idx ON connection(parent_folder_id, sort_order);

CREATE TABLE application_setting (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
