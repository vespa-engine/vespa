// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

/**
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class JksKeyStore {

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

    public KeyStore loadJavaKeyStore() throws Exception {
        try(InputStream stream = Files.newInputStream(keyStoreFile)) {
            KeyStore keystore = KeyStore.getInstance(KEY_STORE_TYPE);
            keystore.load(stream, keyStorePassword != null ? keyStorePassword.toCharArray() : null);
            return keystore;
        }
    }

}
