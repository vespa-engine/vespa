// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author vekterli
 */
public class CipherUtils {

    /**
     * Streams the contents of fromPath into toPath after being wrapped by the input cipher.
     * Depending on the Cipher mode, this either encrypts a plaintext file into ciphertext,
     * or decrypts a ciphertext file into plaintext.
     *
     * @param fromPath source file path to read from
     * @param toPath destination file path to write to
     * @param cipher a Cipher in either ENCRYPT or DECRYPT mode
     * @throws IOException if any file operation fails
     */
    public static void streamEncipherFileContents(Path fromPath, Path toPath, Cipher cipher) throws IOException {
        if (fromPath.equals(toPath)) {
            throw new IllegalArgumentException("Can't use same file as both input and output for enciphering");
        }
        try (var inStream     = Files.newInputStream(fromPath);
             var outStream    = Files.newOutputStream(toPath);
             var cipherStream = new CipherOutputStream(outStream, cipher)) {
            inStream.transferTo(cipherStream);
            cipherStream.flush();
        }
    }

}
