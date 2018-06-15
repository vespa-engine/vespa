package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.DNS_NAME;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class X509CertificateUtilsTest {
    @Test
    public void can_deserialize_serialized_pem_certificate() {
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X500Principal subject = new X500Principal("CN=myservice");
        X509Certificate cert = TestUtils.createCertificate(keypair, subject);
        assertEquals(subject, cert.getSubjectX500Principal());
        String pem = X509CertificateUtils.toPem(cert);
        assertThat(pem, containsString("BEGIN CERTIFICATE"));
        assertThat(pem, containsString("END CERTIFICATE"));
        X509Certificate deserializedCert = X509CertificateUtils.fromPem(pem);
        assertEquals(subject, deserializedCert.getSubjectX500Principal());
    }

    @Test
    public void can_deserialize_serialized_pem_certificate_list() {
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X500Principal subject1 = new X500Principal("CN=myservice");
        X509Certificate cert1 = TestUtils.createCertificate(keypair, subject1);
        X500Principal subject2 = new X500Principal("CN=myservice");
        X509Certificate cert2 = TestUtils.createCertificate(keypair, subject2);
        List<X509Certificate> certificateList = Arrays.asList(cert1, cert2);
        String pem = X509CertificateUtils.toPem(certificateList);
        List<X509Certificate> deserializedCertificateList = X509CertificateUtils.certificateListFromPem(pem);
        assertEquals(2, certificateList.size());
        assertEquals(subject1, deserializedCertificateList.get(0).getSubjectX500Principal());
        assertEquals(subject2, deserializedCertificateList.get(1).getSubjectX500Principal());
    }

    @Test
    public void can_list_subject_alternative_names() {
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X500Principal subject = new X500Principal("CN=myservice");
        SubjectAlternativeName san = new SubjectAlternativeName(DNS_NAME, "dns-san");
        X509Certificate cert = X509CertificateBuilder
                .fromKeypair(
                        keypair,
                        subject,
                        Instant.now(),
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        SignatureAlgorithm.SHA256_WITH_RSA,
                        1)
                .addSubjectAlternativeName(san)
                .build();

        List<SubjectAlternativeName> sans = X509CertificateUtils.getSubjectAlternativeNames(cert);
        assertThat(sans.size(), is(1));
        assertThat(sans.get(0), equalTo(san));
    }
}