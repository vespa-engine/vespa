// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import javax.security.auth.x500.X500Principal;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

import static com.yahoo.security.TestUtils.createCertificate;
import static com.yahoo.security.TestUtils.createKeystoreFile;
import static org.hamcrest.CoreMatchers.isA;


/**
 * @author bjorncs
 */
public class KeyStoreBuilderTest {

    private static final char[] PASSWORD = new char[0];

    @Rule
    public TemporaryFolder tempDirectory = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void can_create_jks_keystore_from_privatekey_and_certificate() throws Exception {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X509Certificate certificate = createCertificate(keyPair);
        KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry("key", keyPair.getPrivate(), certificate)
                .build();
    }

    @Test
    public void can_build_jks_keystore_from_file() throws Exception {
        Path keystoreFile = tempDirectory.newFile().toPath();
        createKeystoreFile(keystoreFile, KeyStoreType.JKS, PASSWORD);

        KeyStoreBuilder.withType(KeyStoreType.JKS)
                .fromFile(keystoreFile, PASSWORD)
                .build();
    }

    @Test
    public void can_build_pcks12_keystore_from_file() throws Exception {
        Path keystoreFile = tempDirectory.newFile().toPath();
        createKeystoreFile(keystoreFile, KeyStoreType.PKCS12, PASSWORD);

        KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                .fromFile(keystoreFile, PASSWORD)
                .build();
    }

    @Test
    public void fails_when_certificate_is_expired() {
        thrown.expectCause(isA(CertificateExpiredException.class));
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        Instant now = Instant.now();
        X509Certificate certificate = createCertificate(keyPair, new X500Principal("cn=subject"), now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry("key", keyPair.getPrivate(), certificate)
                .build();
    }
}