package com.mmlinaric.remotemanager.ssh;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;

/** Enforces saved SSH host keys and asks the user before accepting a previously unknown key. */
public final class KnownHostsVerifier implements HostKeyVerifier {
    public interface Prompt {
        boolean trustUnknown(String host, String address, String algorithm, String fingerprint);

        void warnChanged(String host, String oldFingerprint, String newFingerprint);
    }

    private final Prompt prompt;
    private final OpenSSHKnownHosts knownHosts;

    public KnownHostsVerifier(Path file, Prompt prompt) throws IOException {
        this.prompt = prompt;
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
        knownHosts = new OpenSSHKnownHosts(file.toFile());
    }

    @Override
    public boolean verify(String hostname, int port, PublicKey key) {
        if (knownHosts.verify(hostname, port, key)) {
            return true;
        }

        String adjustedHost = port == 22 ? hostname : "[" + hostname + "]:" + port;
        KeyType algorithm = KeyType.fromKey(key);
        List<String> previous = matchingFingerprints(adjustedHost, algorithm);
        String fingerprint = fingerprint(key);

        if (!previous.isEmpty()) {
            prompt.warnChanged(adjustedHost, String.join(", ", previous), fingerprint);
            return false;
        }

        String address = resolveAddress(hostname);
        if (!prompt.trustUnknown(adjustedHost, address, algorithm.toString(), fingerprint)) {
            return false;
        }

        try {
            OpenSSHKnownHosts.HostEntry entry = new OpenSSHKnownHosts.HostEntry(null, adjustedHost, algorithm, key);
            knownHosts.write(entry);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    @Override
    public List<String> findExistingAlgorithms(String hostname, int port) {
        return knownHosts.findExistingAlgorithms(hostname, port);
    }

    private List<String> matchingFingerprints(String host, KeyType algorithm) {
        List<String> result = new ArrayList<>();
        for (OpenSSHKnownHosts.KnownHostEntry entry : knownHosts.entries()) {
            try {
                if (entry.appliesTo(algorithm, host)) {
                    result.add(fingerprintFromLine(entry.getLine()));
                }
            } catch (IOException ignored) {
                // A malformed entry cannot be used to establish trust.
            }
        }
        return result;
    }

    private static String fingerprint(PublicKey key) {
        byte[] encoded = new Buffer.PlainBuffer().putPublicKey(key).getCompactData();
        return sha256Fingerprint(encoded);
    }

    private static String fingerprintFromLine(String line) {
        String[] parts = line.split("\\s+");
        int keyIndex = parts[0].startsWith("@") ? 3 : 2;
        try {
            return sha256Fingerprint(Base64.getDecoder().decode(parts[keyIndex]));
        } catch (RuntimeException malformed) {
            return "unavailable";
        }
    }

    private static String sha256Fingerprint(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String resolveAddress(String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (IOException error) {
            return "unavailable";
        }
    }
}
