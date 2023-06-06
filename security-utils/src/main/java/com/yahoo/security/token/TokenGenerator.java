// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.token;

import com.yahoo.security.Base62;

import java.security.SecureRandom;

/**
 * <p>
 * Generates new {@link Token} instances that encapsulate a given number of cryptographically
 * secure random bytes and, with a sufficiently high number of bytes (>= 16), can be expected
 * to be globally unique and computationally infeasible to guess or brute force.
 * </p><p>
 * Tokens are returned in a printable and copy/paste-friendly form (Base62) with an optional
 * prefix string.
 * </p><p>
 * Example of token string generated with the prefix "itsa_me_mario_" and 32 random bytes:
 * </p>
 * <pre>
 *     itsa_me_mario_nALfICMyrC4NFagwAkiOdGh80DPS1vSUPprGUKVPLya
 * </pre>
 * <p>
 * Tokens are considered secret information, and must be treated as such.
 * </p>
 */
public class TokenGenerator {

    private static final SecureRandom CSPRNG = new SecureRandom();

    public static Token generateToken(TokenDomain domain, String prefix, int nRandomBytes) {
        if (nRandomBytes <= 0) {
            throw new IllegalArgumentException("Token bytes must be a positive integer");
        }
        byte[] tokenRand = new byte[nRandomBytes];
        CSPRNG.nextBytes(tokenRand);
        return new Token(domain, "%s%s".formatted(prefix, Base62.codec().encode(tokenRand)));
    }

}
