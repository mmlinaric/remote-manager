package com.example.remotemanager.model;

import java.util.UUID;

public record ConnectionFolder(UUID id, UUID parentFolderId, String name, int sortOrder) {
    public ConnectionFolder {
        if (id == null || name == null || name.isBlank()) throw new IllegalArgumentException("Folder name is required");
    }
    @Override public String toString() { return name; }
}
