// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateRequestMetadata.DnsNameStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author andreer
 */
public class CertificatePoolMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final CertificatePoolMaintainer maintainer = new CertificatePoolMaintainer(tester.controller(), new MockMetric(), Duration.ofHours(1), new Random(4));

    @Test
    void new_certs_are_requested_until_limit() {
        tester.flagSource().withIntFlag(Flags.CERT_POOL_SIZE.id(), 3);
        assertNumCerts(1);
        assertNumCerts(2);
        assertNumCerts(3);
        assertNumCerts(3);
    }

    @Test
    void cert_contains_expected_names() {
        tester.flagSource().withIntFlag(Flags.CERT_POOL_SIZE.id(), 1);
        assertNumCerts(1);
        EndpointCertificateMock endpointCertificateProvider = (EndpointCertificateMock) tester.controller().serviceRegistry().endpointCertificateProvider();

        var metadata = endpointCertificateProvider.listCertificates().get(0);

        assertEquals(
                List.of(
                        new DnsNameStatus("*.c8868d4e.z.vespa.oath.cloud", "done"),
                        new DnsNameStatus("*.c8868d4e.g.vespa.oath.cloud", "done"),
                        new DnsNameStatus("*.c8868d4e.a.vespa.oath.cloud", "done")
                ), metadata.dnsNames());

        assertEquals("vespa.tls.preprovisioned.c8868d4e-cert", endpointCertificateProvider.certificateDetails(metadata.requestId()).cert_key_keyname());
        assertEquals("vespa.tls.preprovisioned.c8868d4e-key", endpointCertificateProvider.certificateDetails(metadata.requestId()).private_key_keyname());
    }

    private void assertNumCerts(int n) {
        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        assertEquals(n, tester.curator().readUnassignedCertificates().size());
    }
}
