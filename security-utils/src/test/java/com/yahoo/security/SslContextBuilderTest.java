// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static com.yahoo.security.TestUtils.createCertificate;
import static com.yahoo.security.TestUtils.createKeystore;
import static com.yahoo.security.TestUtils.createKeystoreFile;

/**
 * @author bjorncs
 */
public class SslContextBuilderTest {

    private static final char[] PASSWORD = new char[0];

    @TempDir
    public File tempDirectory;

    @Test
    void can_build_sslcontext_with_truststore_only() throws Exception {
        new SslContextBuilder()
                .withTrustStore(createKeystore(KeyStoreType.JKS, PASSWORD))
                .build();
    }

    @Test
    void can_build_sslcontext_with_keystore_only() throws Exception {
        new SslContextBuilder()
                .withKeyStore(createKeystore(KeyStoreType.JKS, PASSWORD), PASSWORD)
                .build();
    }

    @Test
    void can_build_sslcontext_with_truststore_and_keystore() throws Exception {
        new SslContextBuilder()
                .withKeyStore(createKeystore(KeyStoreType.JKS, PASSWORD), PASSWORD)
                .withTrustStore(createKeystore(KeyStoreType.JKS, PASSWORD))
                .build();
    }

    @Test
    void can_build_sslcontext_with_keystore_from_private_key_and_certificate() throws Exception {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X509Certificate certificate = createCertificate(keyPair);
        new SslContextBuilder()
                .withKeyStore(keyPair.getPrivate(), certificate)
                .build();
    }

    @Test
    void can_build_sslcontext_with_jks_keystore_from_file() throws Exception {
        Path keystoreFile = File.createTempFile("junit", null, tempDirectory).toPath();
        createKeystoreFile(keystoreFile, KeyStoreType.JKS, PASSWORD);

        new SslContextBuilder()
                .withKeyStore(keystoreFile, PASSWORD, KeyStoreType.JKS)
                .build();
    }

    @Test
    void can_build_sslcontext_with_pcks12_keystore_from_file() throws Exception {
        Path keystoreFile = File.createTempFile("junit", null, tempDirectory).toPath();
        createKeystoreFile(keystoreFile, KeyStoreType.PKCS12, PASSWORD);

        new SslContextBuilder()
                .withKeyStore(keystoreFile, PASSWORD, KeyStoreType.PKCS12)
                .build();
    }

}
