// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

}
