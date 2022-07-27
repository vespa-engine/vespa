// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.MutableX509KeyManager;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class MutableX509KeyManagerTest {

    private static final X500Principal SUBJECT = new X500Principal("CN=dummy");

    @Test
    void key_manager_can_be_updated_with_new_certificate() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);

        BigInteger serialNumberInitialCertificate = BigInteger.ONE;
        KeyStore initialKeystore = generateKeystore(keyPair, serialNumberInitialCertificate);

        MutableX509KeyManager keyManager = new MutableX509KeyManager(initialKeystore, new char[0]);

        String[] initialAliases = keyManager.getClientAliases(keyPair.getPublic().getAlgorithm(), new Principal[]{SUBJECT});
        assertThat(initialAliases).hasSize(1);
        X509Certificate[] certChain = keyManager.getCertificateChain(initialAliases[0]);
        assertThat(certChain).hasSize(1);
        assertThat(certChain[0].getSerialNumber()).isEqualTo(serialNumberInitialCertificate);

        BigInteger serialNumberUpdatedCertificate = BigInteger.TEN;
        KeyStore updatedKeystore = generateKeystore(keyPair, serialNumberUpdatedCertificate);
        keyManager.updateKeystore(updatedKeystore, new char[0]);

        String[] updatedAliases = keyManager.getClientAliases(keyPair.getPublic().getAlgorithm(), new Principal[]{SUBJECT});
        assertThat(updatedAliases).hasSize(1);
        X509Certificate[] updatedCertChain = keyManager.getCertificateChain(updatedAliases[0]);
        assertThat(updatedCertChain).hasSize(1);
        assertThat(updatedCertChain[0].getSerialNumber()).isEqualTo(serialNumberUpdatedCertificate);
    }

    private static KeyStore generateKeystore(KeyPair keyPair, BigInteger serialNumber) {
        X509Certificate certificate = X509CertificateBuilder.fromKeypair(
                keyPair, SUBJECT, Instant.EPOCH, Instant.EPOCH.plus(1, DAYS), SignatureAlgorithm.SHA256_WITH_ECDSA, serialNumber)
                .build();
        return KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                .withKeyEntry("default", keyPair.getPrivate(), certificate)
                .build();
    }

}