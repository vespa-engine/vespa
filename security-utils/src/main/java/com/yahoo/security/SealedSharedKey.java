// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.nio.ByteBuffer;

/**
 * A SealedSharedKey represents the public part of a secure one-way ephemeral key exchange.
 *
 * It is "sealed" in the sense that it is expected to be computationally infeasible
 * for anyone to derive the correct shared key from the sealed key without holding
 * the correct private key.
 *
 * A SealedSharedKey can be converted to--and from--an opaque string token representation.
 * This token representation is expected to be used as a convenient serialization
 * form when communicating shared keys.
 */
public record SealedSharedKey(KeyId keyId, byte[] enc, byte[] ciphertext) {

    /** Current encoding version of opaque sealed key tokens. Must be less than 256. */
    public static final int CURRENT_TOKEN_VERSION = 1;
    /** Encryption context for v1 tokens is always a 32-byte X25519 public key */
    public static final int MAX_ENC_CONTEXT_LENGTH = 255;

    public SealedSharedKey {
        if (enc.length > MAX_ENC_CONTEXT_LENGTH) {
            throw new IllegalArgumentException("Encryption context is too large to be encoded (max is %d, got %d)"
                    .formatted(MAX_ENC_CONTEXT_LENGTH, enc.length));
        }
    }

    /**
     * Creates an opaque URL-safe string token that contains enough information to losslessly
     * reconstruct the SealedSharedKey instance when passed verbatim to fromTokenString().
     */
    public String toTokenString() {
        byte[] keyIdBytes = keyId.asBytes();
        // u8 token version || u8 length(key id) || key id || u8 length(enc) || enc || ciphertext
        ByteBuffer encoded = ByteBuffer.allocate(1 + 1 + keyIdBytes.length + 1 + enc.length + ciphertext.length);
        encoded.put((byte)CURRENT_TOKEN_VERSION);
        encoded.put((byte)keyIdBytes.length);
        encoded.put(keyIdBytes);
        encoded.put((byte)enc.length);
        encoded.put(enc);
        encoded.put(ciphertext);
        encoded.flip();

        byte[] encBytes = new byte[encoded.remaining()];
        encoded.get(encBytes);
        return Base62.codec().encode(encBytes);
    }

    /**
     * Attempts to unwrap a SealedSharedKey opaque token representation that was previously
     * created by a call to toTokenString().
     */
    public static SealedSharedKey fromTokenString(String tokenString) {
        verifyInputTokenStringNotTooLarge(tokenString);
        byte[] rawTokenBytes = Base62.codec().decode(tokenString);
        if (rawTokenBytes.length < 1) {
            throw new IllegalArgumentException("Decoded token too small to contain a version");
        }
        ByteBuffer decoded = ByteBuffer.wrap(rawTokenBytes);
        // u8 token version || u8 length(key id) || key id || u8 length(enc) || enc || ciphertext
        int version = Byte.toUnsignedInt(decoded.get());
        if (version != CURRENT_TOKEN_VERSION) {
            throw new IllegalArgumentException("Token had unexpected version. Expected %d, was %d"
                                               .formatted(CURRENT_TOKEN_VERSION, version));
        }
        int keyIdLen = Byte.toUnsignedInt(decoded.get());
        byte[] keyIdBytes = new byte[keyIdLen];
        decoded.get(keyIdBytes);
        int encLen = Byte.toUnsignedInt(decoded.get());
        byte[] enc = new byte[encLen];
        decoded.get(enc);
        byte[] ciphertext = new byte[decoded.remaining()];
        decoded.get(ciphertext);

        return new SealedSharedKey(KeyId.ofBytes(keyIdBytes), enc, ciphertext);
    }

    public int tokenVersion() { return CURRENT_TOKEN_VERSION; }

    private static void verifyInputTokenStringNotTooLarge(String tokenString) {
        // Expected max decoded size for v1 is 3 + 255 + 32 + 32 = 322. For simplicity, round this
        // up to 512 to effectively not have to care about the overhead of any reasonably chosen encoding.
        if (tokenString.length() > 512) {
            throw new IllegalArgumentException("Token string is too long to possibly be a valid token");
        }
    }

}
