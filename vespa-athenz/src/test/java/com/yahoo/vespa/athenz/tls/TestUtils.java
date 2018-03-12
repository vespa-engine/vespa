// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.athenz.auth.util.Crypto;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
class TestUtils {

    static KeyStore createKeystore(KeyStoreType type, char[] password)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        KeyPair keyPair = createKeyPair();
        KeyStore keystore = type.createKeystore();
        keystore.load(null);
        keystore.setKeyEntry("entry-name", keyPair.getPrivate(), password, new Certificate[]{createCertificate(keyPair)});
        return keystore;
    }

    static X509Certificate createCertificate(KeyPair keyPair)
            throws OperatorCreationException, IOException {
        String x500Principal = "CN=mysubject";
        PKCS10CertificationRequest csr =
                Crypto.getPKCS10CertRequest(
                        Crypto.generateX509CSR(keyPair.getPrivate(), x500Principal, null));
        return Crypto.generateX509Certificate(csr, keyPair.getPrivate(), new X500Name(x500Principal), 3600, false);
    }

    static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(4096);
        return keyGen.genKeyPair();
    }

    static void createKeystoreFile(File file, KeyStoreType type, char[] password)
            throws IOException, GeneralSecurityException, OperatorCreationException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            KeyStore keystore = createKeystore(type, password);
            keystore.store(out, password);
        }
    }
}
