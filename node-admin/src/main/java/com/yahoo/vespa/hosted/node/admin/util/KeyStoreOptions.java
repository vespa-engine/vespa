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

public class KeyStoreOptions {
    public final Path path;
    public final char[] password;
    public final String type;

    public KeyStoreOptions(Path path, char[] password, String type) {
        this.path = path;
        this.password = password;
        this.type = type;
    }

    public KeyStore loadKeyStoreWithBcProvider()
            throws IOException, NoSuchProviderException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            KeyStore keyStore = KeyStore.getInstance(type, "BC");
            keyStore.load(in, password);
            return keyStore;
        }
    }
}
