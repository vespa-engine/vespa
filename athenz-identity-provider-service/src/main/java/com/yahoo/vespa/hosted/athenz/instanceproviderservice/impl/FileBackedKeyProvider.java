// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * @author bjorncs
 */
public class FileBackedKeyProvider implements KeyProvider {

    private final String keyPathPrefix;

    public FileBackedKeyProvider(String keyPathPrefix) {
        this.keyPathPrefix = keyPathPrefix;
    }

    @Override
    public String getPrivateKey(int version) {
        return loadKey(new File(keyPathPrefix + ".priv." + version));
    }

    @Override
    public String getPublicKey(int version) {
        return loadKey(new File(keyPathPrefix + ".pub." + version));
    }

    private static String loadKey(File file) {
        try {
            if (!file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("Key missing: " + file.getAbsolutePath());
            }
            return new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
