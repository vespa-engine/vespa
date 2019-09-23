// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class CertificatesTest {

    @Test
    public void expiry() {
        var clock = new ManualClock();
        var certificates = new Certificates(clock);
        var csr = CertificateTester.createCsr();
        var keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        var caCertificate = CertificateTester.createCertificate("CA", keyPair);
        var certificate = certificates.create(csr, caCertificate, keyPair.getPrivate());
        var now = clock.instant();

        assertEquals(now.minus(Duration.ofHours(1)).truncatedTo(SECONDS), certificate.getNotBefore().toInstant());
        assertEquals(now.plus(Duration.ofDays(30)).truncatedTo(SECONDS), certificate.getNotAfter().toInstant());
    }

}
