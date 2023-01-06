// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class SslContextBuilderTest {

    private static Path certificateFile;
    private static Path privateKeyFile;

    @BeforeAll
    static void createPemFiles(@TempDir Path tempDirectory) throws GeneralSecurityException, OperatorCreationException, IOException {
        KeyPair keypair = createKeypair();
        X509Certificate certificate = createCertificate(keypair);
        certificateFile = tempDirectory.resolve("cert.pem");
        privateKeyFile = tempDirectory.resolve("key.pem");
        writePem(certificateFile, "CERTIFICATE", certificate.getEncoded());
        writePem(privateKeyFile, "PRIVATE KEY", keypair.getPrivate().getEncoded());
    }

    @Test
    void successfully_constructs_sslcontext_from_pem_files() {
        SSLContext sslContext = Assertions.assertDoesNotThrow(() ->
                new SslContextBuilder()
                        .withCaCertificates(certificateFile)
                        .withCertificateAndKey(certificateFile, privateKeyFile)
                        .build());
        assertEquals("TLS", sslContext.getProtocol());
    }

    @Test
    void successfully_constructs_sslcontext_when_no_builder_parameter_given() {
        SSLContext sslContext = Assertions.assertDoesNotThrow(() -> new SslContextBuilder().build());
        assertEquals("TLS", sslContext.getProtocol());
    }

    @Test
    void successfully_constructs_sslcontext_with_only_certificate_file() {
        SSLContext sslContext = Assertions.assertDoesNotThrow(() ->
                new SslContextBuilder()
                        .withCertificateAndKey(certificateFile, privateKeyFile)
                        .build());
        assertEquals("TLS", sslContext.getProtocol());
    }

    @Test
    void successfully_constructs_sslcontext_with_only_ca_certificate_file() {
        SSLContext sslContext = Assertions.assertDoesNotThrow(() ->
                new SslContextBuilder()
                        .withCaCertificates(certificateFile)
                        .build());
        assertEquals("TLS", sslContext.getProtocol());
    }

    private static void writePem(Path file, String type, byte[] asn1DerEncodedObject) throws IOException {
        try (BufferedWriter fileWriter = Files.newBufferedWriter(file);
             JcaPEMWriter pemWriter = new JcaPEMWriter(fileWriter)) {
            pemWriter.writeObject(new PemObject(type, asn1DerEncodedObject));
            pemWriter.flush();
        }
    }

    private static KeyPair createKeypair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", SslContextBuilder.bcProvider);
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        return generator.generateKeyPair();
    }

    private static X509Certificate createCertificate(KeyPair keypair) throws OperatorCreationException, CertificateException {
        JcaX509v3CertificateBuilder jcaCertBuilder = new JcaX509v3CertificateBuilder(
                new X500Principal("CN=localhost"), BigInteger.ONE, Date.from(Instant.EPOCH),
                Date.from(Instant.EPOCH.plus(100_000, ChronoUnit.DAYS)), new X500Principal("CN=localhost"), keypair.getPublic());
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(SslContextBuilder.bcProvider)
                .build(keypair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(SslContextBuilder.bcProvider)
                .getCertificate(jcaCertBuilder.build(contentSigner));
    }

}
