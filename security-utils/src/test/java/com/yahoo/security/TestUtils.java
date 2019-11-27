// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

import static com.yahoo.security.KeyStoreUtils.writeKeyStoreToFile;


/**
 * @author bjorncs
 */
class TestUtils {

    static KeyStore createKeystore(KeyStoreType type, char[] password)  {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        return KeyStoreBuilder.withType(type)
                .withKeyEntry("entry-name", keyPair.getPrivate(), password, createCertificate(keyPair))
                .build();
    }

    static X509Certificate createCertificate(KeyPair keyPair)  {
        return createCertificate(keyPair, new X500Principal("CN=mysubject"));
    }

    static X509Certificate createCertificate(KeyPair keyPair, X500Principal subject)  {
        return createCertificate(keyPair, subject, Instant.now(), Instant.now().plus(Duration.ofDays(1)));
    }

    static X509Certificate createCertificate(KeyPair keyPair, X500Principal subject, Instant notBefore, Instant notAfter) {
        return X509CertificateBuilder
                .fromKeypair(
                        keyPair, subject, notBefore, notAfter, SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(1))
                .build();
    }

    static void createKeystoreFile(Path file, KeyStoreType type, char[] password) {
        writeKeyStoreToFile(createKeystore(type, password), file, password);
    }
}
