// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder.KeyStoreType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
public class AthenzSslContextBuilderTest {

    private static final char[] PASSWORD = new char[0];

    @Rule
    public TemporaryFolder tempDirectory = new TemporaryFolder();

    @Test
    public void can_build_sslcontext_with_truststore_only() throws Exception {
        new AthenzSslContextBuilder()
                .withTrustStore(createKeystore("JKS"))
                .build();
    }

    @Test
    public void can_build_sslcontext_with_keystore_only() throws Exception {
        new AthenzSslContextBuilder()
                .withKeyStore(createKeystore("JKS"), PASSWORD)
                .build();
    }

    @Test
    public void can_build_sslcontext_with_truststore_and_keystore() throws Exception {
        new AthenzSslContextBuilder()
                .withKeyStore(createKeystore("JKS"), PASSWORD)
                .withTrustStore(createKeystore("JKS"))
                .build();
    }

    @Test
    public void can_build_sslcontext_with_keystore_from_private_key_and_certificate() throws Exception {
        KeyPair keyPair = createKeyPair();
        X509Certificate certificate = createCertificate(keyPair);
        new AthenzSslContextBuilder()
                .withKeyStore(keyPair.getPrivate(), certificate)
                .build();
    }

    @Test
    public void can_build_sslcontext_with_jks_keystore_from_file() throws Exception {
        KeyStore keystore = createKeystore("JKS");
        File keystoreFile = tempDirectory.newFile();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(keystoreFile))) {
            keystore.store(out, PASSWORD);
        }
        new AthenzSslContextBuilder()
                .withKeyStore(keystoreFile, PASSWORD, KeyStoreType.JKS)
                .build();
    }

    @Test
    public void can_build_sslcontext_with_pcks12_keystore_from_file() throws Exception {
        KeyStore keystore = createKeystore("PKCS12");
        File keystoreFile = tempDirectory.newFile();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(keystoreFile))) {
            keystore.store(out, PASSWORD);
        }
        new AthenzSslContextBuilder()
                .withKeyStore(keystoreFile, PASSWORD, KeyStoreType.PKCS12)
                .build();
    }

    private static KeyStore createKeystore(String type) throws Exception {
        KeyPair keyPair = createKeyPair();
        KeyStore keystore = KeyStore.getInstance(type);
        keystore.load(null);
        keystore.setKeyEntry("entry-name", keyPair.getPrivate(), PASSWORD, new Certificate[]{createCertificate(keyPair)});
        return keystore;
    }

    private static X509Certificate createCertificate(KeyPair keyPair) throws
            OperatorCreationException, IOException {
        String x500Principal = "CN=mysubject";
        PKCS10CertificationRequest csr =
                Crypto.getPKCS10CertRequest(
                        Crypto.generateX509CSR(keyPair.getPrivate(), x500Principal, null));
        return Crypto.generateX509Certificate(csr, keyPair.getPrivate(), new X500Name(x500Principal), 3600, false);
    }

    private static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        return keyGen.genKeyPair();
    }
}
