package com.mmlinaric.remotemanager.vault;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A password-free summary of a vault entry suitable for lists, selectors, and validation. */
public record VaultEntry(
        UUID id,
        String title,
        String username,
        Map<String, String> fields,
        List<String> attachments,
        boolean hasPassword) {
    public VaultEntry(UUID id, String title, String username, Map<String, String> fields, List<String> attachments) {
        this(id, title, username, fields, attachments, false);
    }

    @Override
    public String toString() {
        return title == null || title.isBlank() ? id.toString() : title;
    }
}
