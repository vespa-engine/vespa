// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author vekterli
 */
public class CipherUtils {

    /**
     * Streams the contents of an input stream into an output stream after being wrapped by the input cipher.
     * Depending on the Cipher mode, this either encrypts a plaintext stream into ciphertext,
     * or decrypts a ciphertext stream into plaintext.
     *
     * @param input source stream to read from
     * @param output destination stream to write to
     * @param cipher a Cipher in either ENCRYPT or DECRYPT mode
     * @throws IOException if any file operation fails
     */
    public static void streamEncipher(InputStream input, OutputStream output, Cipher cipher) throws IOException {
        try (var cipherStream = new CipherOutputStream(output, cipher)) {
            input.transferTo(cipherStream);
            cipherStream.flush();
        }
    }

}
