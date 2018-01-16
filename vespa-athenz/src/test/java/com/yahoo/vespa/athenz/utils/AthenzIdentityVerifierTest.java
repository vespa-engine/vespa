package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

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

    private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, AthenzIdentity identity)
            throws OperatorCreationException, CertIOException, CertificateException {
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X500Name x500Name = new X500Name("CN="+ identity.getFullName());
        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(Duration.ofDays(30)));

        X509v3CertificateBuilder certificateBuilder =
                new JcaX509v3CertificateBuilder(
                        x500Name, BigInteger.valueOf(now.toEpochMilli()), notBefore, notAfter, x500Name, keyPair.getPublic()
                )
                        .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certificateBuilder.build(contentSigner));

    }

    private static SSLSession createSslSessionMock(X509Certificate certificate) throws SSLPeerUnverifiedException {
        SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[]{certificate});
        return sslSession;
    }

}