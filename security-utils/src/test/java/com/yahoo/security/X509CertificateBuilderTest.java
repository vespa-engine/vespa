// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class X509CertificateBuilderTest {

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {KeyAlgorithm.RSA, 2048, SignatureAlgorithm.SHA512_WITH_RSA},
                {KeyAlgorithm.EC, 256, SignatureAlgorithm.SHA512_WITH_ECDSA}});
    }

    private KeyAlgorithm keyAlgorithm;
    private int keySize;
    private SignatureAlgorithm signatureAlgorithm;

    public void initX509CertificateBuilderTest(KeyAlgorithm keyAlgorithm,
                                                int keySize,
                                                SignatureAlgorithm signatureAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
        this.keySize = keySize;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    @MethodSource("data")
    @ParameterizedTest(name = "{0}")
    void can_build_self_signed_certificate(KeyAlgorithm keyAlgorithm, int keySize, SignatureAlgorithm signatureAlgorithm) {
        initX509CertificateBuilderTest(keyAlgorithm, keySize, signatureAlgorithm);
        KeyPair keyPair = KeyUtils.generateKeypair(keyAlgorithm, keySize);
        X500Principal subject = new X500Principal("CN=myservice");
        X509Certificate cert =
                X509CertificateBuilder.fromKeypair(
                        keyPair,
                        subject,
                        Instant.now(),
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        signatureAlgorithm,
                        BigInteger.valueOf(1))
                        .setBasicConstraints(true, true)
                        .build();
        assertEquals(subject, cert.getSubjectX500Principal());
    }

    @MethodSource("data")
    @ParameterizedTest(name = "{0}")
    void can_build_certificate_from_csr(KeyAlgorithm keyAlgorithm, int keySize, SignatureAlgorithm signatureAlgorithm) {
        initX509CertificateBuilderTest(keyAlgorithm, keySize, signatureAlgorithm);
        X500Principal subject = new X500Principal("CN=subject");
        X500Principal issuer = new X500Principal("CN=issuer");
        KeyPair csrKeypair = KeyUtils.generateKeypair(keyAlgorithm, keySize);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, csrKeypair, signatureAlgorithm).build();
        KeyPair caKeypair = KeyUtils.generateKeypair(keyAlgorithm, keySize);
        X509Certificate cert = X509CertificateBuilder
                .fromCsr(
                        csr,
                        issuer,
                        Instant.now(),
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        caKeypair.getPrivate(),
                        signatureAlgorithm,
                        BigInteger.valueOf(1))
                .addSubjectAlternativeName("subject1.alt")
                .addSubjectAlternativeName("subject2.alt")
                .build();
        assertEquals(subject, cert.getSubjectX500Principal());
    }

}