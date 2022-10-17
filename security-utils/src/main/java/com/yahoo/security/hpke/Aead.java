// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

/**
 * Authenticated encryption with associated data (AEAD)
 *
 * @author vekterli
 */
public interface Aead {

    /**
     * @param key Symmetric key bytes for encryption
     * @param nonce Nonce to use for the encryption
     * @param aad Associated authenticated data that will <em>not</em> be encrypted
     * @param pt Plaintext to seal
     * @return resulting ciphertext
     */
    byte[] seal(byte[] key, byte[] nonce, byte[] aad, byte[] pt);

    /**
     * @param key Symmetric key bytes for decryption
     * @param nonce Nonce to use for the decryption
     * @param aad Associated authenticated data to verify
     * @param ct ciphertext to decrypt
     * @return resulting plaintext
     */
    byte[] open(byte[] key, byte[] nonce, byte[] aad, byte[] ct);

    /** The length in bytes of a key for this algorithm. */
    short nK();
    /** The length in bytes of a nonce for this algorithm. */
    short nN();
    /** The length in bytes of the authentication tag for this algorithm. */
    short nT();
    /** Predefined AEAD ID, as given in RFC 9180 section 7.3 */
    short aeadId();

    static Aead aesGcm128() {
        return AesGcm128.getInstance();
    }

}
