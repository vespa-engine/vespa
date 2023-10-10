// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author bjorncs
 */
public class KeyStoreUtils {
    private KeyStoreUtils() {}

    public static void writeKeyStoreToFile(KeyStore keyStore, Path file, char[] password) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
            keyStore.store(out, password);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

    }

    public static void writeKeyStoreToFile(KeyStore keyStore, Path file) {
        writeKeyStoreToFile(keyStore, file, new char[0]);
    }

}
