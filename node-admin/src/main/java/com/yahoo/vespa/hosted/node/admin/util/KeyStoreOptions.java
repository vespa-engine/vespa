// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.KeyStoreUtils;

import java.nio.file.Path;
import java.security.KeyStore;

public class KeyStoreOptions {
    public final Path path;
    public final char[] password;
    public final KeyStoreType keyStoreType;

    public KeyStoreOptions(Path path, char[] password, String type) {
        this.path = path;
        this.password = password;
        this.keyStoreType = KeyStoreType.valueOf(type);
    }

    public KeyStore loadKeyStore() {
        return KeyStoreBuilder
                .withType(keyStoreType)
                .fromFile(path.toFile(), password)
                .build();
    }

    public void storeKeyStore(KeyStore keyStore) {
        KeyStoreUtils.writeKeyStoreToFile(keyStore, path.toFile(), password);
    }
}
