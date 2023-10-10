// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.crypto.SecretKey;

/**
 * A SecretSharedKey represents a pairing of both the secret and public parts of
 * a secure one-way ephemeral key exchange.
 *
 * The underlying SealedSharedKey may be made public, generally as a token.
 *
 * It should not come as a surprise that the underlying SecretKey must NOT be
 * made public.
 */
public record SecretSharedKey(SecretKey secretKey, SealedSharedKey sealedSharedKey) {

    // Explicitly override toString to ensure we can't leak any SecretKey contents
    // via an implicitly generated method. Only print the sealed key (which is entirely public).
    @Override
    public String toString() {
        return "SharedSecretKey(sealed: %s)".formatted(sealedSharedKey.toTokenString());
    }

    /**
     * @return an encryption cipher that matches the version of the SealedSharedKey bound to
     *         the secret shared key
     */
    public AeadCipher makeEncryptionCipher() {
        var version = sealedSharedKey.tokenVersion();
        return switch (version) {
            case 1  -> SharedKeyGenerator.makeAesGcmEncryptionCipher(this);
            case 2  -> SharedKeyGenerator.makeChaCha20Poly1305EncryptionCipher(this);
            default -> throw new IllegalStateException("Unsupported token version: " + version);
        };
    }

    /**
     * @return a decryption cipher that matches the version of the SealedSharedKey bound to
     *         the secret shared key. In other words, the cipher shall match the cipher algorithm
     *         used to perform the encryption this key was used for.
     */
    public AeadCipher makeDecryptionCipher() {
        var version = sealedSharedKey.tokenVersion();
        return switch (version) {
            case 1  -> SharedKeyGenerator.makeAesGcmDecryptionCipher(this);
            case 2  -> SharedKeyGenerator.makeChaCha20Poly1305DecryptionCipher(this);
            default -> throw new IllegalStateException("Unsupported token version: " + version);
        };
    }

}
