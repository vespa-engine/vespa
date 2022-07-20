// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca;

import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.security.SubjectAlternativeName.Type.DNS;

/**
 * Helper class for creating {@link X509Certificate}s.
 *
 * @author mpolden
 */
public class Certificates {

    private static final Duration CERTIFICATE_TTL = Duration.ofDays(30);
    private static final String INSTANCE_ID_DELIMITER = ".instanceid.athenz.";

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
        var builder = X509CertificateBuilder.fromCsr(csr,
                                              x500principal,
                                              notBefore,
                                              notAfter,
                                              caPrivateKey,
                                              SHA256_WITH_ECDSA,
                                              X509CertificateBuilder.generateRandomSerialNumber());
        for (var san : csr.getSubjectAlternativeNames()) {
            builder = builder.addSubjectAlternativeName(san.decode());
        }
        return builder.build();
    }

    /** Returns instance ID parsed from the Subject Alternative Names in given csr */
    public static String instanceIdFrom(Pkcs10Csr csr) {
        return getInstanceIdFromSAN(csr.getSubjectAlternativeNames())
                .orElseThrow(() -> new IllegalArgumentException("No instance ID found in CSR"));
    }

    public static Optional<String> instanceIdFrom(X509Certificate certificate) {
        return getInstanceIdFromSAN(X509CertificateUtils.getSubjectAlternativeNames(certificate));
    }

    private static Optional<String> getInstanceIdFromSAN(List<SubjectAlternativeName> subjectAlternativeNames) {
        return subjectAlternativeNames.stream()
                .filter(san -> san.getType() == DNS)
                .map(SubjectAlternativeName::getValue)
                .map(Certificates::parseInstanceId)
                .flatMap(Optional::stream)
                .map(VespaUniqueInstanceId::asDottedString)
                .findFirst();
    }

    private static Optional<VespaUniqueInstanceId> parseInstanceId(String dnsName) {
        var delimiterStart = dnsName.indexOf(INSTANCE_ID_DELIMITER);
        if (delimiterStart == -1) return Optional.empty();
        dnsName = dnsName.substring(0, delimiterStart);
        try {
            return Optional.of(VespaUniqueInstanceId.fromDottedString(dnsName));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static String getSubjectAlternativeNames(Pkcs10Csr csr, SubjectAlternativeName.Type sanType) {
        return csr.getSubjectAlternativeNames().stream()
                .map(SubjectAlternativeName::decode)
                .filter(san -> san.getType() == sanType)
                .map(SubjectAlternativeName::getValue)
                .collect(Collectors.joining(","));
    }
}
