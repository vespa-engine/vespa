// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrBuilder;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateBuilder;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;

/**
 * Helper class for creating certificates, CSRs etc. for testing purposes.
 *
 * @author mpolden
 */
public class CertificateTester {

    private CertificateTester() {}

    public static X509Certificate createCertificate() {
        var keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        return createCertificate("subject", keyPair);
    }

    public static X509Certificate createCertificate(String cn, KeyPair keyPair) {
        var subject = new X500Principal("CN=" + cn);
        return X509CertificateBuilder.fromKeypair(keyPair,
                                                  subject,
                                                  Instant.EPOCH,
                                                  Instant.EPOCH.plus(Duration.ofMinutes(1)),
                                                  SHA256_WITH_ECDSA,
                                                  BigInteger.ONE)
                                     .build();
    }

    public static Pkcs10Csr createCsr() {
        return createCsr(List.of(), List.of());
    }

    public static Pkcs10Csr createCsr(String dnsName) {
        return createCsr(List.of(dnsName), List.of());
    }

    public static Pkcs10Csr createCsr(List<String> dnsNames) {
        return createCsr(dnsNames, List.of());
    }

    public static Pkcs10Csr createCsr(String cn, List<String> dnsNames) {
        return createCsr(cn, dnsNames, List.of());
    }

    public static Pkcs10Csr createCsr(List<String> dnsNames, List<String> ipAddresses) {
        return createCsr("subject", dnsNames, ipAddresses);
    }
    public static Pkcs10Csr createCsr(String cn, List<String> dnsNames, List<String> ipAddresses) {
        X500Principal subject = new X500Principal("CN=" + cn);
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        var builder = Pkcs10CsrBuilder.fromKeypair(subject, keyPair, SignatureAlgorithm.SHA512_WITH_ECDSA);
        for (var dnsName : dnsNames) {
            builder = builder.addSubjectAlternativeName(SubjectAlternativeName.Type.DNS, dnsName);
        }
        for (var ipAddress : ipAddresses) {
            builder = builder.addSubjectAlternativeName(SubjectAlternativeName.Type.IP, ipAddress);
        }
        return builder.build();
    }

}
