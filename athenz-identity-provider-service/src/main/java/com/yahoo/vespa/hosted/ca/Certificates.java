// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca;

import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateBuilder;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.security.SubjectAlternativeName.Type.DNS_NAME;

/**
 * Helper class for creating {@link X509Certificate}s.
 *
 * @author mpolden
 */
public class Certificates {

    private static final Duration CERTIFICATE_TTL = Duration.ofDays(30);

    private final Clock clock;

    public Certificates(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must be non-null");
    }

    /** Create a new certificate from csr signed by the given CA private key */
    public X509Certificate create(Pkcs10Csr csr, X509Certificate caCertificate, PrivateKey caPrivateKey) {
        var x500principal = caCertificate.getSubjectX500Principal();
        var now = clock.instant();
        var notBefore = now.minus(Duration.ofHours(1));
        var notAfter = now.plus(CERTIFICATE_TTL);
        return X509CertificateBuilder.fromCsr(csr,
                                              x500principal,
                                              notBefore,
                                              notAfter,
                                              caPrivateKey,
                                              SHA256_WITH_ECDSA,
                                              X509CertificateBuilder.generateRandomSerialNumber())
                                     .build();
    }

    /** Returns the DNS name field from Subject Alternative Names in given csr */
    public static String extractDnsName(Pkcs10Csr csr) {
        return csr.getSubjectAlternativeNames().stream()
                  .filter(san -> san.getType() == DNS_NAME)
                  .map(SubjectAlternativeName::getValue)
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("DNS name not found in CSR"));
    }

}
