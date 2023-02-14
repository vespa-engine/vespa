// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static com.yahoo.security.ArrayUtils.hex;

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
public record SealedSharedKey(int version, KeyId keyId, byte[] enc, byte[] ciphertext) {

    /** Current encoding version of opaque sealed key tokens. Must be less than 256. */
    public static final int CURRENT_TOKEN_VERSION = 2;
    /** Encryption context for v{1,2} tokens is always a 32-byte X25519 public key */
    public static final int MAX_ENC_CONTEXT_LENGTH = 255;
    // Expected max decoded size for v1 is 3 + 255 + 32 + 32 = 322. For simplicity, round this
    // up to 512 to effectively not have to care about the overhead of any reasonably chosen encoding.
    public static final int MAX_TOKEN_STRING_LENGTH = 512;

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
        return Base62.codec().encode(toSerializedBytes());
    }

    byte[] toSerializedBytes() {
        byte[] keyIdBytes = keyId.asBytes();
        // u8 token version || u8 length(key id) || key id || u8 length(enc) || enc || ciphertext
        ByteBuffer encoded = ByteBuffer.allocate(1 + 1 + keyIdBytes.length + 1 + enc.length + ciphertext.length);
        encoded.put((byte)version);
        encoded.put((byte)keyIdBytes.length);
        encoded.put(keyIdBytes);
        encoded.put((byte)enc.length);
        encoded.put(enc);
        encoded.put(ciphertext);
        encoded.flip();

        byte[] encBytes = new byte[encoded.remaining()];
        encoded.get(encBytes);
        return encBytes;
    }

    /**
     * Attempts to unwrap a SealedSharedKey opaque token representation that was previously
     * created by a call to toTokenString().
     */
    public static SealedSharedKey fromTokenString(String tokenString) {
        verifyInputTokenStringNotTooLarge(tokenString);
        byte[] rawTokenBytes = Base62.codec().decode(tokenString);
        return fromSerializedBytes(rawTokenBytes);
    }

    static SealedSharedKey fromSerializedBytes(byte[] rawTokenBytes) {
        if (rawTokenBytes.length < 1) {
            throw new IllegalArgumentException("Decoded token too small to contain a version");
        }
        ByteBuffer decoded = ByteBuffer.wrap(rawTokenBytes);
        // u8 token version || u8 length(key id) || key id || u8 length(enc) || enc || ciphertext
        int version = Byte.toUnsignedInt(decoded.get());
        if (version < 1 || version > CURRENT_TOKEN_VERSION) {
            throw new IllegalArgumentException("Token had unexpected version. Expected value in [1, %d], was %d"
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

        return new SealedSharedKey(version, KeyId.ofBytes(keyIdBytes), enc, ciphertext);
    }

    public int tokenVersion() { return version; }

    private static void verifyInputTokenStringNotTooLarge(String tokenString) {
        if (tokenString.length() > MAX_TOKEN_STRING_LENGTH) {
            throw new IllegalArgumentException("Token string is too long to possibly be a valid token");
        }
    }

    // Friendlier toString() with hex dump of enc/ciphertext fields
    @Override
    public String toString() {
        return "SealedSharedKey{" +
                "version=" + version +
                ", keyId=" + keyId +
                ", enc=" + hex(enc) +
                ", ciphertext=" + hex(ciphertext) +
                '}';
    }

    // Explicitly generated equals() and hashCode() to use _contents_ of
    // enc/ciphertext arrays, and not just their refs.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SealedSharedKey that = (SealedSharedKey) o;
        return version == that.version && keyId.equals(that.keyId) &&
                Arrays.equals(enc, that.enc) &&
                Arrays.equals(ciphertext, that.ciphertext);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, keyId);
        result = 31 * result + Arrays.hashCode(enc);
        result = 31 * result + Arrays.hashCode(ciphertext);
        return result;
    }

}
