// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ZipEntriesTest {

    @Test
    public void test_replacement() {
        ApplicationPackage applicationPackage = new ApplicationPackage(new byte[0]);
        List<X509Certificate> certificates = IntStream.range(0, 3)
                                                      .mapToObj(i -> {
                                                          KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
                                                          X500Principal subject = new X500Principal("CN=subject" + i);
                                                          return X509CertificateBuilder.fromKeypair(keyPair,
                                                                                                    subject,
                                                                                                    Instant.now(),
                                                                                                    Instant.now().plusSeconds(1),
                                                                                                    SignatureAlgorithm.SHA512_WITH_ECDSA,
                                                                                                    BigInteger.valueOf(1))
                                                                                       .build();
                                                      })
                                                      .collect(Collectors.toUnmodifiableList());
        
        assertEquals(List.of(), applicationPackage.trustedCertificates());
        for (int i = 0; i < certificates.size(); i++) {
            applicationPackage = applicationPackage.withTrustedCertificate(certificates.get(i));
            assertEquals(certificates.subList(0, i + 1), applicationPackage.trustedCertificates());
        }
    }

}
