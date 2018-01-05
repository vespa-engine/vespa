package com.yahoo.vespa.hosted.node.admin.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

class KeyStoreOptions {
    public final Path path;
    public final char[] password;
    public final String type;

    public KeyStoreOptions(Path path, char[] password, String type) {
        this.path = path;
        this.password = password;
        this.type = type;
    }

    public KeyStore getKeyStore() throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(fis, password);

            return keyStore;
        }
    }
}
