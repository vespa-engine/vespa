// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Optional;

public class KeyStoreOptions {
    public final Path path;
    public final char[] password;
    public final String type;
    private final Optional<String> provider;

    public KeyStoreOptions(Path path, char[] password, String type) {
        this(path, password, type, null);
    }

    public KeyStoreOptions(Path path, char[] password, String type, String provider) {
        this.path = path;
        this.password = password;
        this.type = type;
        this.provider = Optional.ofNullable(provider);
    }

    public KeyStore loadKeyStore()
            throws IOException, NoSuchProviderException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            KeyStore keyStore = getKeyStoreInstance();
            keyStore.load(in, password);
            return keyStore;
        }
    }

    public KeyStore getKeyStoreInstance() throws NoSuchProviderException, KeyStoreException {
        return provider.isPresent() ?
                KeyStore.getInstance(type, provider.get()) :
                KeyStore.getInstance(type);
    }
}
