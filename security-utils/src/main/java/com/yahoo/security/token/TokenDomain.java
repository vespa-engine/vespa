// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.token;

import java.util.Arrays;

import static com.yahoo.security.ArrayUtils.toUtf8Bytes;

/**
 * <p>A token domain controls how token fingerprints and check-hashes are derived from
 * a particular token. Even with identical token contents, different domain contexts
 * are expected to produce entirely different derivations (with an extremely high
 * probability).
 * </p><p>
 * Since tokens are just opaque sequences of high entropy bytes (with an arbitrary
 * prefix), they do not by themselves provide any kind of inherent domain separation.
 * Token domains exist to allow for <em>explicit</em> domain separation between
 * different usages of tokens.
 * </p><p>
 * Fingerprint contexts will usually be the same across an entire deployment of a token
 * evaluation infrastructure, in order to allow for identifying tokens "globally"
 * across that deployment.
 * </p><p>
 * Access check hash contexts should be unique for each logical token evaluation audience,
 * ensuring that access hashes from an unrelated audience (with a different context) can
 * never be made to match, be it accidentally or deliberately.
 * </p>
 */
public record TokenDomain(byte[] fingerprintContext, byte[] checkHashContext) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenDomain that = (TokenDomain) o;
        return Arrays.equals(fingerprintContext, that.fingerprintContext) &&
                Arrays.equals(checkHashContext, that.checkHashContext);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(fingerprintContext);
        result = 31 * result + Arrays.hashCode(checkHashContext);
        return result;
    }

    public static TokenDomain of(String fingerprintContext, String checkHashContext) {
        return new TokenDomain(toUtf8Bytes(fingerprintContext),
                               toUtf8Bytes(checkHashContext));
    }

}
