// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.AEADBlockCipher;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * AEAD cipher wrapper to hide the underlying crypto provider used.
 *
 * @author vekterli
 */
public class AeadCipher {

    private final AEADBlockCipher cipher;

    private AeadCipher(AEADBlockCipher cipher) {
        this.cipher = cipher;
    }

    static AeadCipher of(AEADBlockCipher cipher) {
        return new AeadCipher(cipher);
    }

    /**
     * Returns a wrapping <code>OutputStream</code> that, depending on the cipher mode, either
     * encrypts or decrypts all data that is written to it before passing it on to <code>out</code>.
     */
    public OutputStream wrapOutputStream(OutputStream out) {
        return new CipherOutputStream(out, cipher);
    }

    /**
     * Returns a wrapping <code>InputStream</code> that, depending on the cipher mode, either
     * encrypts or decrypts all data that is read from the underlying input stream.
     */
    public InputStream wrapInputStream(InputStream in) {
        return new CipherInputStream(in, cipher);
    }

}
