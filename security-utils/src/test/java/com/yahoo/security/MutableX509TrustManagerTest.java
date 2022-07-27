// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class MutableX509TrustManagerTest {

    @Test
    void key_manager_can_be_updated_with_new_certificate() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);

        X509Certificate initialCertificate = generateCertificate(new X500Principal("CN=issuer1"), keyPair);
        KeyStore initialTruststore = generateTruststore(initialCertificate);

        MutableX509TrustManager trustManager = new MutableX509TrustManager(initialTruststore);

        X509Certificate[] initialAcceptedIssuers = trustManager.getAcceptedIssuers();
        assertThat(initialAcceptedIssuers).containsExactly(initialCertificate);

        X509Certificate updatedCertificate = generateCertificate(new X500Principal("CN=issuer2"), keyPair);
        KeyStore updatedTruststore = generateTruststore(updatedCertificate);
        trustManager.updateTruststore(updatedTruststore);

        X509Certificate[] updatedAcceptedIssuers = trustManager.getAcceptedIssuers();
        assertThat(updatedAcceptedIssuers).containsExactly(updatedCertificate);
    }

    private static X509Certificate generateCertificate(X500Principal issuer, KeyPair keyPair) {
        return X509CertificateBuilder.fromKeypair(
                keyPair, issuer, Instant.EPOCH, Instant.EPOCH.plus(1, DAYS), SignatureAlgorithm.SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
    }

    private static KeyStore generateTruststore(X509Certificate certificate) {
        return KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                .withCertificateEntry("default", certificate)
                .build();
    }

}