// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.tls.X509CertificateBuilder;
import org.junit.Test;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

import static com.yahoo.vespa.athenz.tls.SignatureAlgorithm.SHA256_WITH_RSA;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzIdentityVerifierTest {

    @Test
    public void verifies_certificate_with_athenz_service_as_common_name() throws Exception {
        AthenzIdentity trustedIdentity = new AthenzService("mydomain", "alice");
        AthenzIdentity unknownIdentity = new AthenzService("mydomain", "mallory");
        KeyPair keyPair = createKeyPair();
        AthenzIdentityVerifier verifier = new AthenzIdentityVerifier(singleton(trustedIdentity));
        assertTrue(verifier.verify("hostname", createSslSessionMock(createSelfSignedCertificate(keyPair, trustedIdentity))));
        assertFalse(verifier.verify("hostname", createSslSessionMock(createSelfSignedCertificate(keyPair, unknownIdentity))));
    }

    private static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        return keyGen.generateKeyPair();
    }

    private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, AthenzIdentity identity) {
        X500Principal x500Name = new X500Principal("CN="+ identity.getFullName());
        Instant now = Instant.now();
        return X509CertificateBuilder
                .fromKeypair(keyPair, x500Name, now, now.plus(Duration.ofDays(30)), SHA256_WITH_RSA, 1)
                .setBasicConstraints(true, true)
                .build();
    }

    private static SSLSession createSslSessionMock(X509Certificate certificate) throws SSLPeerUnverifiedException {
        SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[]{certificate});
        return sslSession;
    }

}
