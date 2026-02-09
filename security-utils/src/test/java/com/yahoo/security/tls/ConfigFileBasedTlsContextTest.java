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

import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class ConfigFileBasedTlsContextTest {

    @TempDir
    public File tempDirectory;

    @Test
    void can_create_sslcontext_from_credentials() throws IOException, InterruptedException {
        KeyPair keyPair = KeyUtils.generateKeypair(EC);
        Path privateKeyFile = File.createTempFile("junit", null, tempDirectory).toPath();
        Files.write(privateKeyFile, KeyUtils.toPem(keyPair.getPrivate()).getBytes(UTF_8));

        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(keyPair, new X500Principal("CN=dummy"), EPOCH, EPOCH.plus(1, DAYS), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
        Path certificateChainFile = File.createTempFile("junit", null, tempDirectory).toPath();
        String certificatePem = X509CertificateUtils.toPem(certificate);
        Files.write(certificateChainFile, certificatePem.getBytes(UTF_8));

        Path caCertificatesFile = File.createTempFile("junit", null, tempDirectory).toPath();
        Files.write(caCertificatesFile, certificatePem.getBytes(UTF_8));

        TransportSecurityOptions options = new TransportSecurityOptions.Builder()
                .withCertificates(certificateChainFile, privateKeyFile)
                .withCaCertificates(caCertificatesFile)
                .build();

        Path optionsFile = File.createTempFile("junit", null, tempDirectory).toPath();
        options.toJsonFile(optionsFile);

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

}
