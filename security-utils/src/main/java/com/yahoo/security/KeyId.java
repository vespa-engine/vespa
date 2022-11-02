// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.util.Arrays;
import java.util.Objects;

import static com.yahoo.security.ArrayUtils.fromUtf8Bytes;
import static com.yahoo.security.ArrayUtils.toUtf8Bytes;

/**
 * Represents a named key ID comprising an arbitrary (but length-limited)
 * sequence of valid UTF-8 bytes.
 *
 * @author vekterli
 */
public class KeyId {

    // Max length MUST be possible to fit in an unsigned byte; see SealedSharedKey token encoding/decoding.
    public static final int MAX_KEY_ID_UTF8_LENGTH = 255;

    private final byte[] keyIdBytes;

    private KeyId(byte[] keyIdBytes) {
        if (keyIdBytes.length > MAX_KEY_ID_UTF8_LENGTH) {
            throw new IllegalArgumentException("Key ID is too large to be encoded (max is %d, got %d)"
                                               .formatted(MAX_KEY_ID_UTF8_LENGTH, keyIdBytes.length));
        }
        verifyByteStringRoundtripsAsValidUtf8(keyIdBytes);
        this.keyIdBytes = keyIdBytes;
    }

    /**
     * Construct a KeyId containing the given sequence of bytes.
     *
     * @param keyIdBytes array of valid UTF-8 bytes. May be zero-length, but not null.
     *                   Note: to avoid accidental mutations, the key bytes are deep-copied.
     * @return a new KeyId instance
     */
    public static KeyId ofBytes(byte[] keyIdBytes) {
        Objects.requireNonNull(keyIdBytes);
        return new KeyId(Arrays.copyOf(keyIdBytes, keyIdBytes.length));
    }

    /**
     * Construct a KeyId containing the UTF-8 byte representation of the given string.
     *
     * @param keyId a string whose UTF-8 byte representation will be the key ID. May be
     *              zero-length but not null.
     * @return a new KeyId instance
     */
    public static KeyId ofString(String keyId) {
        Objects.requireNonNull(keyId);
        return new KeyId(toUtf8Bytes(keyId));
    }

    /**
     * @return the raw backing byte array. <strong>Must therefore not be mutated.</strong>
     */
    public byte[] asBytes() { return keyIdBytes; }

    public String asString() { return fromUtf8Bytes(keyIdBytes); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyId keyId = (KeyId) o;
        return Arrays.equals(keyIdBytes, keyId.keyIdBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(keyIdBytes);
    }

    @Override
    public String toString() {
        return "KeyId(%s)".formatted(asString());
    }

    private static void verifyByteStringRoundtripsAsValidUtf8(byte[] byteStr) {
        String asStr   = fromUtf8Bytes(byteStr); // Replaces bad chars with a placeholder
        byte[] asBytes = toUtf8Bytes(asStr);
        if (!Arrays.equals(byteStr, asBytes)) {
            throw new IllegalArgumentException("Key ID is not valid normalized UTF-8");
        }
    }

}
