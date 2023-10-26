// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import io.airlift.compress.zstd.ZstdInputStream;
import com.yahoo.compress.ZstdOutputStream;
import com.yahoo.security.AeadCipher;

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
     * @param cipher an {@link AeadCipher} created for either encryption or decryption
     * @throws IOException if any file operation fails
     */
    public static void streamEncipher(InputStream input, OutputStream output, AeadCipher cipher) throws IOException {
        try (var cipherStream = cipher.wrapOutputStream(output)) {
            input.transferTo(cipherStream);
            cipherStream.flush();
        }
    }

    private static OutputStream maybeWrapCompress(OutputStream out, boolean compressZstd) throws IOException {
        return compressZstd ? new ZstdOutputStream(out) : out;
    }

    public static void streamEncrypt(InputStream input, OutputStream output, AeadCipher cipher, boolean compressZstd) throws IOException {
        try (var out = maybeWrapCompress(cipher.wrapOutputStream(output), compressZstd)) {
            input.transferTo(out);
            out.flush();
        }
    }

    private static InputStream maybeWrapDecompress(InputStream in, boolean decompressZstd) throws IOException {
        return decompressZstd ? new ZstdInputStream(in) : in;
    }

    public static void streamDecrypt(InputStream input, OutputStream output, AeadCipher cipher, boolean decompressZstd) throws IOException {
        try (var in = maybeWrapDecompress(cipher.wrapInputStream(input), decompressZstd)) {
            in.transferTo(output);
            output.flush();
        }
    }

}
