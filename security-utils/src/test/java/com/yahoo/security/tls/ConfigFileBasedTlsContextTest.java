// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLEngine;
import javax.security.auth.x500.X500Principal;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
public class ConfigFileBasedTlsContextTest {

    @TempDir
    public File tempDirectory;

    @Test
    void can_create_sslcontext_from_credentials() throws IOException {
        Path optionsFile = writeOptionsFile(createOptionsBuilder().build());

        try (TlsContext tlsContext = new ConfigFileBasedTlsContext(optionsFile, AuthorizationMode.ENFORCE)) {
            SSLEngine sslEngine = tlsContext.createSslEngine();
            assertThat(sslEngine).isNotNull();
            String[] enabledCiphers = sslEngine.getEnabledCipherSuites();
            assertThat(enabledCiphers).isNotEmpty();
            assertThat(enabledCiphers).isSubsetOf(TlsContext.ALLOWED_CIPHER_SUITES.toArray(new String[0]));

            String[] enabledProtocols = sslEngine.getEnabledProtocols();
            assertThat(enabledProtocols).contains("TLSv1.2");
        }
    }

    @Test
    void authorized_peers_are_reloaded() throws Exception {
        Path optionsFile = writeOptionsFile(createOptionsBuilder().withAuthorizedPeers(authorizedPeersWithCn("other")).build());

        try (ConfigFileBasedTlsContext tlsContext = new ConfigFileBasedTlsContext(optionsFile, AuthorizationMode.ENFORCE)) {
            var trustManager = tlsContext.sslContext().trustManager();
            X509Certificate[] chain = new X509Certificate[] { certificate };
            assertThrows(PeerAuthorizationFailedException.class, () -> trustManager.checkClientTrusted(chain, "EC"));

            createOptionsBuilder().withAuthorizedPeers(authorizedPeersWithCn("dummy")).build().toJsonFile(optionsFile);
            tlsContext.reload();
            trustManager.checkClientTrusted(chain, "EC");
        }
    }

    private X509Certificate certificate;
    private TransportSecurityOptions.Builder optionsBuilder;

    // Credentials are generated once per test, so reloading options keeps the same certificate
    private TransportSecurityOptions.Builder createOptionsBuilder() throws IOException {
        if (optionsBuilder != null) return optionsBuilder;
        KeyPair keyPair = KeyUtils.generateKeypair(EC);
        Path privateKeyFile = File.createTempFile("junit", null, tempDirectory).toPath();
        Files.write(privateKeyFile, KeyUtils.toPem(keyPair.getPrivate()).getBytes(UTF_8));

        certificate = X509CertificateBuilder
                .fromKeypair(keyPair, new X500Principal("CN=dummy"), EPOCH, EPOCH.plus(100_000, DAYS), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
        Path certificateChainFile = File.createTempFile("junit", null, tempDirectory).toPath();
        String certificatePem = X509CertificateUtils.toPem(certificate);
        Files.write(certificateChainFile, certificatePem.getBytes(UTF_8));

        Path caCertificatesFile = File.createTempFile("junit", null, tempDirectory).toPath();
        Files.write(caCertificatesFile, certificatePem.getBytes(UTF_8));

        optionsBuilder = new TransportSecurityOptions.Builder()
                .withCertificates(certificateChainFile, privateKeyFile)
                .withCaCertificates(caCertificatesFile);
        return optionsBuilder;
    }

    private Path writeOptionsFile(TransportSecurityOptions options) throws IOException {
        Path optionsFile = File.createTempFile("junit", null, tempDirectory).toPath();
        options.toJsonFile(optionsFile);
        return optionsFile;
    }

    private static AuthorizedPeers authorizedPeersWithCn(String cn) {
        return new AuthorizedPeers(Set.of(new PeerPolicy("policy", List.of(RequiredPeerCredential.of(RequiredPeerCredential.Field.CN, cn)))));
    }

}
