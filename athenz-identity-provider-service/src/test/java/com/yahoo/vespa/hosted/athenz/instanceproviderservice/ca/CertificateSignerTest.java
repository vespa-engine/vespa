// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.yahoo.test.ManualClock;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

    private final KeyPair clientKeyPair = getKeyPair();

    private final long startTime = 1234567890000L;
    private final KeyPair caKeyPair = getKeyPair();
    private final String cfgServerHostname = "cfg1.us-north-1.vespa.domain.tld";
    private final ManualClock clock = new ManualClock(Instant.ofEpochMilli(startTime));
    private final CertificateSigner signer = new CertificateSigner(caKeyPair.getPrivate(), cfgServerHostname, clock);

    private final String requestersHostname = "tenant-123.us-north-1.vespa.domain.tld";

    @Test
    public void test_signing() throws Exception {
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        String subject = "C=NO,OU=Vespa,CN=" + requestersHostname;
        PKCS10CertificationRequest request = makeRequest(subject, extGen.generate());

        X509Certificate certificate = signer.generateX509Certificate(request, requestersHostname);
        assertCertificate(certificate, subject, Collections.singleton(Extension.basicConstraints.getId()));
    }

    @Test
    public void common_name_test() throws Exception {
        CertificateSigner.verifyCertificateCommonName(
                new X500Name("CN=" + requestersHostname), requestersHostname);
        CertificateSigner.verifyCertificateCommonName(
                new X500Name("C=NO,OU=Vespa,CN=" + requestersHostname), requestersHostname);
        CertificateSigner.verifyCertificateCommonName(
                new X500Name("C=NO+OU=org,CN=" + requestersHostname), requestersHostname);

        assertCertificateCommonNameException("C=NO", "Only 1 common name should be set");
        assertCertificateCommonNameException("C=US+CN=abc123.domain.tld,C=NO+CN=" + requestersHostname, "Only 1 common name should be set");
        assertCertificateCommonNameException("CN=evil.hostname.domain.tld",
                "Remote hostname tenant-123.us-north-1.vespa.domain.tld does not match common name evil.hostname.domain.tld");
    }

    @Test(expected = IllegalArgumentException.class)
    public void extensions_test_subject_alternative_names() throws Exception {
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "some.other.domain.tld")}));
        PKCS10CertificationRequest request = makeRequest("OU=Vespa", extGen.generate());

        CertificateSigner.verifyCertificateExtensions(request);
    }

    @Test
    public void extensions_allowed() throws Exception {
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen.addExtension(Extension.certificateIssuer, true, new byte[0]);
        PKCS10CertificationRequest request = makeRequest("OU=Vespa", extGen.generate());

        CertificateSigner.verifyCertificateExtensions(request);
    }

    private void assertCertificateCommonNameException(String subject, String expectedMessage) {
        try {
            CertificateSigner.verifyCertificateCommonName(new X500Name(subject), requestersHostname);
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
        assertEquals(CertificateSigner.SIGNER_ALGORITHM, certificate.getSigAlgName());
        assertEquals(expectedSubjectName, certificate.getSubjectDN().getName());
        assertEquals("CN=" + cfgServerHostname, certificate.getIssuerX500Principal().getName());

        Set<String> extensions = Stream.of(certificate.getNonCriticalExtensionOIDs(),
                certificate.getCriticalExtensionOIDs())
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        assertEquals(expectedExtensions, extensions);

        certificate.verify(caKeyPair.getPublic());
    }

    private PKCS10CertificationRequest makeRequest(String subject, Extensions extensions) throws Exception {
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(
                new X500Name(subject), clientKeyPair.getPublic());
        builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions);

        ContentSigner signGen = new JcaContentSignerBuilder(CertificateSigner.SIGNER_ALGORITHM).build(caKeyPair.getPrivate());
        return builder.build(signGen);
    }

    private static KeyPair getKeyPair() {
        try {
            return KeyPairGenerator.getInstance("RSA").genKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
