package com.yahoo.vespa.hosted.node.certificate;

import com.yahoo.test.ManualClock;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class CertificateRefresherTest {

    private final ManualClock clock = new ManualClock();
    private final String commonName = "CertificateRefresherTest";
    private final Duration certificateExpiration = Duration.ofDays(5);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void write_new_certificate() throws Exception {
        X509Certificate firstCertificate = makeCertificate(1);
        X509Certificate secondCertificate = makeCertificate(2);
        CertificateAuthorityClient caClient = mock(CertificateAuthorityClient.class);
        when(caClient.signCsr(any())).thenReturn(firstCertificate, secondCertificate);

        Path keyStorePath = tempFolder.getRoot().toPath().resolve("some/path/keystore.p12");
        CertificateRefresher certificateRefresher = new CertificateRefresher(caClient, clock);

        certificateRefresher.refreshCertificate(keyStorePath, commonName);
        assertEquals(firstCertificate, CertificateRefresher.readCertificate(keyStorePath));

        // Calling it again before a third of certificate lifetime has passed has no effect
        certificateRefresher.refreshCertificate(keyStorePath, commonName);
        assertEquals(firstCertificate, CertificateRefresher.readCertificate(keyStorePath));

        // After a third of the expiration time passes, we should refresh the certificate
        clock.advance(certificateExpiration.dividedBy(3).plusSeconds(1));
        certificateRefresher.refreshCertificate(keyStorePath, commonName);
        assertEquals(secondCertificate, CertificateRefresher.readCertificate(keyStorePath));

        verify(caClient, times(2)).signCsr(any());
    }

    private X509Certificate makeCertificate(int serial)
            throws CertificateException, InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchProviderException {
        try {
            KeyPair keyPair = CertificateRefresher.generateKeyPair();
            X500Name subject = new X500Name("CN=" + commonName);
            Date notBefore = Date.from(clock.instant());
            Date notAfter = Date.from(clock.instant().plus(certificateExpiration));

            JcaX509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(subject,
                    BigInteger.valueOf(serial), notBefore, notAfter, subject, keyPair.getPublic());
            ContentSigner sigGen = new JcaContentSignerBuilder(CertificateRefresher.SIGNER_ALGORITHM)
                    .build(keyPair.getPrivate());
            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider())
                    .getCertificate(certGen.build(sigGen));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}