// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author bjorncs
 */
public class KeyStoreUtils {
    private KeyStoreUtils() {}

    public static void writeKeyStoreToFile(KeyStore keyStore, File file, char[] password) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            keyStore.store(out, password);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

    }

    public static void writeKeyStoreToFile(KeyStore keyStore, File file) {
        writeKeyStoreToFile(keyStore, file, new char[0]);
    }

}
