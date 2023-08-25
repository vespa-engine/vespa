// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProviderMock;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateRequest.DnsNameStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author andreer
 */
public class CertificatePoolMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final CertificatePoolMaintainer maintainer = new CertificatePoolMaintainer(tester.controller(), new MockMetric(), Duration.ofHours(1));

    @Test
    void new_certs_are_requested_until_limit() {
        tester.flagSource().withIntFlag(PermanentFlags.CERT_POOL_SIZE.id(), 3);
        assertNumCerts(1);
        assertNumCerts(2);
        assertNumCerts(3);
        assertNumCerts(3);
    }

    @Test
    void cert_contains_expected_names() {
        tester.flagSource().withIntFlag(PermanentFlags.CERT_POOL_SIZE.id(), 1);
        assertNumCerts(1);
        EndpointCertificateProviderMock endpointCertificateProvider = (EndpointCertificateProviderMock) tester.controller().serviceRegistry().endpointCertificateProvider();

        var request = endpointCertificateProvider.listCertificates().get(0);

        assertEquals(
                List.of(
                        new DnsNameStatus("*.f5549014.z.vespa.oath.cloud", "done"),
                        new DnsNameStatus("*.f5549014.g.vespa.oath.cloud", "done"),
                        new DnsNameStatus("*.f5549014.a.vespa.oath.cloud", "done")
                ), request.dnsNames());

        assertEquals("vespa.tls.preprovisioned.f5549014-cert", endpointCertificateProvider.certificateDetails(request.requestId()).certKeyKeyname());
        assertEquals("vespa.tls.preprovisioned.f5549014-key", endpointCertificateProvider.certificateDetails(request.requestId()).privateKeyKeyname());
    }

    private void assertNumCerts(int n) {
        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        assertEquals(n, tester.curator().readUnassignedCertificates().size());
    }

}
