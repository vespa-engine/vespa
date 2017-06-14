// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * @author tonytv
 */
public class JKSKeyStore extends SslKeyStore {

    private static final String keyStoreType = "JKS";
    private final Path keyStoreFile;

    public JKSKeyStore(Path keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    @Override
    public KeyStore loadJavaKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        try(InputStream stream = Files.newInputStream(keyStoreFile)) {
            KeyStore keystore = KeyStore.getInstance(keyStoreType);
            keystore.load(stream, getKeyStorePassword().map(String::toCharArray).orElse(null));
            return keystore;
        }
    }

}
