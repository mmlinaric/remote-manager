package com.mmlinaric.remotemanager.vault;

import java.util.Arrays;

/** An attachment supplied with an identity draft. */
public final class VaultAttachment {
    private final String name;
    private final byte[] bytes;

    public VaultAttachment(String name, byte[] bytes) {
        this.name = name;
        this.bytes = bytes == null ? null : bytes.clone();
    }

    public String name() {
        return name;
    }

    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }

    /** Wipes the binary data retained by this object. */
    public void clear() {
        if (bytes != null) Arrays.fill(bytes, (byte) 0);
    }
}
