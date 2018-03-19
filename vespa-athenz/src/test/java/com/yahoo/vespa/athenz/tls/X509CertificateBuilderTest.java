package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class X509CertificateBuilderTest {

    @Test
    public void can_build_self_signed_certificate() throws NoSuchAlgorithmException {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X500Principal subject = new X500Principal("CN=myservice");
        X509Certificate cert =
                X509CertificateBuilder.fromKeypair(
                        keyPair,
                        subject,
                        Instant.now(),
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        SignatureAlgorithm.SHA256_WITH_RSA,
                        1)
                .setBasicConstraints(true, true)
                .build();
        assertEquals(subject, cert.getSubjectX500Principal());
    }

    @Test
    public void can_build_certificate_from_csr() {
        X500Principal subject = new X500Principal("CN=subject");
        X500Principal issuer = new X500Principal("CN=issuer");
        KeyPair csrKeypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, csrKeypair, SignatureAlgorithm.SHA256_WITH_RSA).build();
        KeyPair caKeypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X509Certificate cert = X509CertificateBuilder
                .fromCsr(
                        csr,
                        issuer,
                        Instant.now(),
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        caKeypair.getPrivate(),
                        SignatureAlgorithm.SHA256_WITH_RSA,
                        1)
                .addSubjectAlternativeName("subject1.alt")
                .addSubjectAlternativeName("subject2.alt")
                .build();
        assertEquals(subject, cert.getSubjectX500Principal());
    }

}