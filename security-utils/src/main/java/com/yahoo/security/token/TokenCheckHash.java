// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.token;

import java.util.Arrays;

import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.unhex;

/**
 * A token check hash represents a hash derived from a token in such a way that
 * distinct "audiences" for the token compute entirely different hashes even for
 * identical token values.
 */
public record TokenCheckHash(byte[] hashBytes) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenCheckHash tokenCheckHash = (TokenCheckHash) o;
        // We don't consider token hashes secret data, so no harm in data-dependent equals()
        return Arrays.equals(hashBytes, tokenCheckHash.hashBytes);
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

    public static TokenCheckHash of(Token token, int nHashBytes) {
        return new TokenCheckHash(token.toDerivedBytes(nHashBytes, token.domain().checkHashContext()));
    }

    public static TokenCheckHash ofRawBytes(byte[] hashBytes) {
        return new TokenCheckHash(Arrays.copyOf(hashBytes, hashBytes.length));
    }

    public static TokenCheckHash ofHex(String hex) { return ofRawBytes(unhex(hex)); }

}
