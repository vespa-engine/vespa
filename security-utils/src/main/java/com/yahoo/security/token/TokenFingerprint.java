// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.token;

import java.util.Arrays;

import static com.yahoo.security.ArrayUtils.hex;

/**
 * <p>A token fingerprint represents an opaque sequence of bytes that is expected
 * to globally identify any particular token within a particular token domain.
 * </p><p>
 * Token fingerprints should not be used directly for access checks; use derived
 * {@link TokenCheckHash} instances for this purpose.
 * </p>
 */
public record TokenFingerprint(byte[] hashBytes) {

    public static final int FINGERPRINT_BITS  = 128;
    public static final int FINGERPRINT_BYTES = FINGERPRINT_BITS / 8;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenFingerprint that = (TokenFingerprint) o;
        // We don't consider token fingerprints secret data, so no harm in data-dependent equals()
        return Arrays.equals(hashBytes, that.hashBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hashBytes);
    }

    public String toHexString() {
        return hex(hashBytes);
    }

    @Override
    public String toString() {
        return toHexString();
    }

    public static TokenFingerprint of(Token token) {
        return new TokenFingerprint(token.toDerivedBytes(FINGERPRINT_BYTES, token.domain().fingerprintContext()));
    }

    public static TokenFingerprint ofRawBytes(byte[] hashBytes) {
        return new TokenFingerprint(Arrays.copyOf(hashBytes, hashBytes.length));
    }

}
