package com.yahoo.vespa.athenz.tls;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static com.yahoo.vespa.athenz.tls.TestUtils.createCertificate;
import static com.yahoo.vespa.athenz.tls.TestUtils.createKeyPair;
import static com.yahoo.vespa.athenz.tls.TestUtils.createKeystoreFile;

/**
 * @author bjorncs
 */
public class KeyStoreBuilderTest {

    private static final char[] PASSWORD = new char[0];

    @Rule
    public TemporaryFolder tempDirectory = new TemporaryFolder();

    @Test
    public void can_create_jks_keystore_from_privatekey_and_certificate() throws Exception {
        KeyPair keyPair = createKeyPair();
        X509Certificate certificate = createCertificate(keyPair);
        KeyStoreBuilder.withType(KeyStoreType.JKS)
                .withKeyEntry("key", keyPair.getPrivate(), certificate)
                .build();
    }

    @Test
    public void can_build_jks_keystore_from_file() throws Exception {
        File keystoreFile = tempDirectory.newFile();
        createKeystoreFile(keystoreFile, KeyStoreType.JKS, PASSWORD);

        KeyStoreBuilder.withType(KeyStoreType.JKS)
                .fromFile(keystoreFile, PASSWORD)
                .build();
    }

    @Test
    public void can_build_pcks12_keystore_from_file() throws Exception {
        File keystoreFile = tempDirectory.newFile();
        createKeystoreFile(keystoreFile, KeyStoreType.PKCS12, PASSWORD);

        KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                .fromFile(keystoreFile, PASSWORD)
                .build();
    }

}