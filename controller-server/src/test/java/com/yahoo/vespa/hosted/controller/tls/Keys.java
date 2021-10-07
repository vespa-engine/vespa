// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tls;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

/**
 * @author mpolden
 */
public class Keys {

    public static final KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
    public static final X509Certificate certificate = createCertificate(keyPair);

    private static X509Certificate createCertificate(KeyPair keyPair)  {
        Instant now = Instant.now();
        return X509CertificateBuilder.fromKeypair(keyPair, new X500Principal("CN=localhost"), now,
                                                  now.plus(Duration.ofDays(1)),
                                                  SignatureAlgorithm.SHA512_WITH_ECDSA,
                                                  BigInteger.valueOf(1))
                                     .build();
    }

}
