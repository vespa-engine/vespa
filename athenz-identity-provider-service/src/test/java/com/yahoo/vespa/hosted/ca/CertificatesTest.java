// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.test.ManualClock;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author mpolden
 */
public class CertificatesTest {

    private final KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
    private final X509Certificate caCertificate = CertificateTester.createCertificate("CA", keyPair);

    @Test
    void expiry() {
        var clock = new ManualClock();
        var certificates = new Certificates(clock);
        var csr = CertificateTester.createCsr();
        var certificate = certificates.create(csr, caCertificate, keyPair.getPrivate());
        var now = clock.instant();

        assertEquals(now.minus(Duration.ofHours(1)).truncatedTo(SECONDS), certificate.getNotBefore().toInstant());
        assertEquals(now.plus(Duration.ofDays(30)).truncatedTo(SECONDS), certificate.getNotAfter().toInstant());
    }

    @Test
    void add_san_from_csr() throws Exception {
        var certificates = new Certificates(new ManualClock());
        var dnsName = "host.example.com";
        var ip = "192.0.2.42";
        var csr = CertificateTester.createCsr(List.of(dnsName), List.of(ip));
        var certificate = certificates.create(csr, caCertificate, keyPair.getPrivate());

        assertNotNull(certificate.getSubjectAlternativeNames());
        assertEquals(2, certificate.getSubjectAlternativeNames().size());

        var subjectAlternativeNames = List.copyOf(certificate.getSubjectAlternativeNames());
        assertEquals(List.of(SubjectAlternativeName.Type.DNS.getTag(), dnsName),
                subjectAlternativeNames.get(0));
        assertEquals(List.of(SubjectAlternativeName.Type.IP.getTag(), ip),
                subjectAlternativeNames.get(1));
    }

    @Test
    void parse_instance_id() {
        var instanceId = "1.cluster1.default.app1.tenant1.us-north-1.prod.node";
        var instanceIdWithSuffix = instanceId + ".instanceid.athenz.dev-us-north-1.vespa.aws.oath.cloud";
        var csr = CertificateTester.createCsr(List.of("foo", "bar", instanceIdWithSuffix));
        assertEquals(instanceId, Certificates.instanceIdFrom(csr));
    }

}
