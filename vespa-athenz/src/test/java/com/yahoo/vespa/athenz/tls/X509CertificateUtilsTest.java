package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class X509CertificateUtilsTest {
    @Test
    public void can_deserialize_serialized_pem_certificate() {
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X500Principal subject = new X500Principal("CN=myservice");
        X509Certificate cert = X509CertificateBuilder
                .fromKeypair(
                        keypair,
                        subject,
                        Instant.now(),
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        SignatureAlgorithm.SHA256_WITH_RSA,
                        1)
                .build();
        assertEquals(subject, cert.getSubjectX500Principal());
        X509Certificate deserializedCert = X509CertificateUtils.fromPem(X509CertificateUtils.toPem(cert));
        assertEquals(subject, deserializedCert.getSubjectX500Principal());
    }


}