// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.jks;

import com.yahoo.jdisc.http.ssl.SslKeyStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class JksKeyStore implements SslKeyStore {

    private static final String KEY_STORE_TYPE = "JKS";
    private final Path keyStoreFile;
    private final String keyStorePassword;

    public JksKeyStore(Path keyStoreFile) {
        this(keyStoreFile, null);
    }

    public JksKeyStore(Path keyStoreFile, String keyStorePassword) {
        this.keyStoreFile = keyStoreFile;
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @Override
    public KeyStore loadJavaKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        try(InputStream stream = Files.newInputStream(keyStoreFile)) {
            KeyStore keystore = KeyStore.getInstance(KEY_STORE_TYPE);
            keystore.load(stream, keyStorePassword != null ? keyStorePassword.toCharArray() : null);
            return keystore;
        }
    }

}
