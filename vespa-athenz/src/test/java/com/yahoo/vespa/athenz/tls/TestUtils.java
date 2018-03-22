// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.yahoo.vespa.athenz.tls.KeyStoreUtils.writeKeyStoreToFile;

/**
 * @author bjorncs
 */
class TestUtils {

    static KeyStore createKeystore(KeyStoreType type, char[] password)  {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 4096);
        return KeyStoreBuilder.withType(type)
                .withKeyEntry("entry-name", keyPair.getPrivate(), password, createCertificate(keyPair))
                .build();
    }

    static X509Certificate createCertificate(KeyPair keyPair)  {
        X500Principal subject = new X500Principal("CN=mysubject");
        return X509CertificateBuilder
                .fromKeypair(
                        keyPair, subject, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA256_WITH_RSA, 1)
                .build();
    }

    static void createKeystoreFile(File file, KeyStoreType type, char[] password) {
        writeKeyStoreToFile(createKeystore(type, password), file, password);
    }
}
