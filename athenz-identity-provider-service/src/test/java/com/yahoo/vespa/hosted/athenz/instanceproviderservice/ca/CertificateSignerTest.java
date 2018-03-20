// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.tls.Extension;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrBuilder;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author freva
 */
public class CertificateSignerTest {

    private final long startTime = 1234567890000L;
    private final KeyPair caKeyPair = getKeyPair();
    private final String cfgServerHostname = "cfg1.us-north-1.vespa.domain.tld";
    private final ManualClock clock = new ManualClock(Instant.ofEpochMilli(startTime));
    private final CertificateSigner signer = new CertificateSigner(caKeyPair.getPrivate(), cfgServerHostname, clock);

    private final String requestersHostname = "tenant-123.us-north-1.vespa.domain.tld";

    @Test
    public void test_signing() throws Exception {
        String subject = String.format("CN=%s,OU=Vespa,C=NO", requestersHostname);
        Pkcs10Csr csr = createCsrBuilder(subject).build();

        X509Certificate certificate = signer.generateX509Certificate(csr, requestersHostname);
        assertCertificate(certificate, subject, Collections.singleton(Extension.BASIC_CONSTRAINS.getOId()));
    }

    @Test
    public void common_name_test() throws Exception {
        CertificateSigner.verifyCertificateCommonName(
                new X500Principal("CN=" + requestersHostname), requestersHostname);
        CertificateSigner.verifyCertificateCommonName(
                new X500Principal("C=NO,OU=Vespa,CN=" + requestersHostname), requestersHostname);
        CertificateSigner.verifyCertificateCommonName(
                new X500Principal("C=NO+OU=org,CN=" + requestersHostname), requestersHostname);

        assertCertificateCommonNameException("C=NO", "Only 1 common name should be set");
        assertCertificateCommonNameException("C=US+CN=abc123.domain.tld,C=NO+CN=" + requestersHostname, "Only 1 common name should be set");
        assertCertificateCommonNameException("CN=evil.hostname.domain.tld",
                "Remote hostname tenant-123.us-north-1.vespa.domain.tld does not match common name evil.hostname.domain.tld");
    }

    @Test(expected = IllegalArgumentException.class)
    public void extensions_test_subject_alternative_names() throws Exception {
        Pkcs10Csr csr = createCsrBuilder("OU=Vespa")
                .addSubjectAlternativeName("some.other.domain.tld")
                .build();
        CertificateSigner.verifyCertificateExtensions(csr);
    }

    private void assertCertificateCommonNameException(String subject, String expectedMessage) {
        try {
            CertificateSigner.verifyCertificateCommonName(new X500Principal(subject), requestersHostname);
            fail("Expected to fail");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private void assertCertificate(X509Certificate certificate, String expectedSubjectName, Set<String> expectedExtensions) throws Exception {
        assertEquals(3, certificate.getVersion());
        assertEquals(BigInteger.valueOf(startTime), certificate.getSerialNumber());
        assertEquals(startTime, certificate.getNotBefore().getTime());
        assertEquals(startTime + CertificateSigner.CERTIFICATE_EXPIRATION.toMillis(), certificate.getNotAfter().getTime());
        assertEquals(CertificateSigner.SIGNER_ALGORITHM.getAlgorithmName(), certificate.getSigAlgName());
        assertEquals(new X500Principal(expectedSubjectName), certificate.getSubjectX500Principal());
        assertEquals("CN=" + cfgServerHostname, certificate.getIssuerX500Principal().getName());

        Set<String> extensions = Stream.of(certificate.getNonCriticalExtensionOIDs(),
                certificate.getCriticalExtensionOIDs())
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        assertEquals(expectedExtensions, extensions);

        certificate.verify(caKeyPair.getPublic());
    }

    private Pkcs10CsrBuilder createCsrBuilder(String subject) {
        return Pkcs10CsrBuilder.fromKeypair(new X500Principal(subject), caKeyPair, CertificateSigner.SIGNER_ALGORITHM);
    }

    private static KeyPair getKeyPair() {
        return KeyUtils.generateKeypair(KeyAlgorithm.RSA);
    }
}
