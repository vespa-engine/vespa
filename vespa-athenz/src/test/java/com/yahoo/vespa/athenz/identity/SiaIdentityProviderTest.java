package com.yahoo.vespa.athenz.identity;

import com.google.common.io.Files;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.KeyStoreUtils;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.SignatureAlgorithm;
import com.yahoo.vespa.athenz.tls.X509CertificateBuilder;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * @author bjorncs
 */
public class SiaIdentityProviderTest {

    @Rule
    public TemporaryFolder tempDirectory = new TemporaryFolder();

    @Test
    public void constructs_ssl_context_from_file() throws IOException {
        File keyFile = tempDirectory.newFile();
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        createPrivateKeyFile(keyFile, keypair);

        X509Certificate certificate = createCertificate(keypair);
        File certificateFile = tempDirectory.newFile();
        createCertificateFile(certificate, certificateFile);

        File trustStoreFile = tempDirectory.newFile();
        createTrustStoreFile(certificate, trustStoreFile);

        SiaIdentityProvider provider =
                new SiaIdentityProvider(
                        new AthenzService("domain", "service-name"),
                        keyFile,
                        certificateFile,
                        trustStoreFile,
                        mock(ScheduledExecutorService.class));

        assertNotNull(provider.getIdentitySslContext());
    }

    private void createPrivateKeyFile(File keyFile, KeyPair keypair) throws IOException {
        String privateKeyPem = KeyUtils.toPem(keypair.getPrivate());
        Files.write(privateKeyPem, keyFile, StandardCharsets.UTF_8);
    }

    private void createCertificateFile(X509Certificate certificate, File certificateFile) throws IOException {
        String certificatePem = X509CertificateUtils.toPem(certificate);
        Files.write(certificatePem, certificateFile, StandardCharsets.UTF_8);
    }

    private X509Certificate createCertificate(KeyPair keypair) {
        Instant now = Instant.now();
        return X509CertificateBuilder
                .fromKeypair(
                        keypair,
                        new X500Principal("CN=subject"),
                        now,
                        now.plus(Duration.ofDays(1)),
                        SignatureAlgorithm.SHA256_WITH_RSA,
                        1)
                .build();
    }

    private void createTrustStoreFile(X509Certificate certificate, File trustStoreFile) {
        KeyStore keystore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withCertificateEntry("dummy-cert", certificate)
                .build();
        KeyStoreUtils.writeKeyStoreToFile(keystore, trustStoreFile);
    }

}