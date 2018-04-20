// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static com.yahoo.vespa.athenz.tls.TestUtils.createCertificate;
import static com.yahoo.vespa.athenz.tls.TestUtils.createKeystore;
import static com.yahoo.vespa.athenz.tls.TestUtils.createKeystoreFile;

/**
 * @author bjorncs
 */
public class SslContextBuilderTest {

    private static final char[] PASSWORD = new char[0];

    @Rule
    public TemporaryFolder tempDirectory = new TemporaryFolder();

    @Test
    public void can_build_sslcontext_with_truststore_only() throws Exception {
        new SslContextBuilder()
                .withTrustStore(createKeystore(KeyStoreType.JKS, PASSWORD))
                .build();
    }

    @Test
    public void can_build_sslcontext_with_keystore_only() throws Exception {
        new SslContextBuilder()
                .withKeyStore(createKeystore(KeyStoreType.JKS, PASSWORD), PASSWORD)
                .build();
    }

    @Test
    public void can_build_sslcontext_with_truststore_and_keystore() throws Exception {
        new SslContextBuilder()
                .withKeyStore(createKeystore(KeyStoreType.JKS, PASSWORD), PASSWORD)
                .withTrustStore(createKeystore(KeyStoreType.JKS, PASSWORD))
                .build();
    }

    @Test
    public void can_build_sslcontext_with_keystore_from_private_key_and_certificate() throws Exception {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X509Certificate certificate = createCertificate(keyPair);
        new SslContextBuilder()
                .withKeyStore(keyPair.getPrivate(), certificate)
                .build();
    }

    @Test
    public void can_build_sslcontext_with_jks_keystore_from_file() throws Exception {
        File keystoreFile = tempDirectory.newFile();
        createKeystoreFile(keystoreFile, KeyStoreType.JKS, PASSWORD);

        new SslContextBuilder()
                .withKeyStore(keystoreFile, PASSWORD, KeyStoreType.JKS)
                .build();
    }

    @Test
    public void can_build_sslcontext_with_pcks12_keystore_from_file() throws Exception {
        File keystoreFile = tempDirectory.newFile();
        createKeystoreFile(keystoreFile, KeyStoreType.PKCS12, PASSWORD);

        new SslContextBuilder()
                .withKeyStore(keystoreFile, PASSWORD, KeyStoreType.PKCS12)
                .build();
    }

}
