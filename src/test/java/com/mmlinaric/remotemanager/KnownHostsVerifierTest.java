package com.mmlinaric.remotemanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mmlinaric.remotemanager.ssh.KnownHostsVerifier;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnownHostsVerifierTest {
    @TempDir
    Path temp;

    @Test
    void requiresTrustForUnknownHostAndRejectsChangedKey() throws Exception {
        Path file = temp.resolve("known_hosts");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = generator.generateKeyPair().getPublic();
        var changed = generator.generateKeyPair().getPublic();

        RecordingPrompt prompt = new RecordingPrompt();
        KnownHostsVerifier verifier = new KnownHostsVerifier(file, prompt);
        assertTrue(verifier.verify("localhost", 22, first));
        assertTrue(prompt.unknownShown);

        prompt.unknownShown = false;
        KnownHostsVerifier reloaded = new KnownHostsVerifier(file, prompt);
        assertTrue(reloaded.verify("localhost", 22, first));
        assertFalse(prompt.unknownShown);

        assertFalse(reloaded.verify("localhost", 22, changed));
        assertTrue(prompt.changedShown);
        assertFalse(prompt.unknownShown);
    }

    @Test
    void rejectsEd25519KeyWhenHostWasTrustedWithRsa() throws Exception {
        assertChangedAcrossAlgorithms(publicKey("RSA"), publicKey("Ed25519"), "LOCALHOST", 2222);
    }

    @Test
    void rejectsRsaKeyWhenHostWasTrustedWithEd25519() throws Exception {
        assertChangedAcrossAlgorithms(publicKey("Ed25519"), publicKey("RSA"), "localhost", 22);
    }

    private void assertChangedAcrossAlgorithms(PublicKey trusted, PublicKey changed, String presentedHost, int port)
            throws Exception {
        Path file = temp.resolve("known_hosts-" + trusted.getAlgorithm());
        RecordingPrompt prompt = new RecordingPrompt();
        KnownHostsVerifier verifier = new KnownHostsVerifier(file, prompt);
        assertTrue(verifier.verify("localhost", port, trusted));

        prompt.reset();
        KnownHostsVerifier reloaded = new KnownHostsVerifier(file, prompt);
        assertFalse(reloaded.verify(presentedHost, port, changed));
        assertTrue(prompt.changedShown);
        assertFalse(prompt.unknownShown);
    }

    private static PublicKey publicKey(String algorithm) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        if (algorithm.equals("RSA")) generator.initialize(2048);
        return generator.generateKeyPair().getPublic();
    }

    private static final class RecordingPrompt implements KnownHostsVerifier.Prompt {
        private boolean unknownShown;
        private boolean changedShown;

        @Override
        public boolean trustUnknown(String host, String address, String algorithm, String fingerprint) {
            unknownShown = true;
            return true;
        }

        @Override
        public void warnChanged(String host, String oldFingerprint, String newFingerprint) {
            changedShown = true;
        }

        private void reset() {
            unknownShown = false;
            changedShown = false;
        }
    }
}
