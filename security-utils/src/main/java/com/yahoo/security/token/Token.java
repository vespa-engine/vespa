// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.token;

import com.yahoo.security.HKDF;

import java.util.Objects;

import static com.yahoo.security.ArrayUtils.toUtf8Bytes;

/**
 * <p>A token represents an arbitrary, opaque sequence of secret bytes (preferably from a secure
 * random source) whose possession gives the holder the right to some resource(s) or action(s).
 * For a token to be recognized it must be presented in its entirety, i.e. bitwise exact. This
 * includes any (optional) text prefixes.
 * </p><p>
 * Only the party <em>presenting</em> the token should store the token secret itself; any
 * parties that need to identify and/or verify the token should store <em>derivations</em>
 * of the token instead (TokenFingerprint and TokenCheckHash, respectively).
 * </p><p>
 * A Token <em>object</em> is bound to a particular TokenDomain, but any given secret token
 * string may be used to create many Token objects for any number of domains; it is opaque and
 * not in and by itself tied to any specific domain.
 * </p>
 */
public class Token {

    private final TokenDomain domain;
    private final String secretTokenString;
    private final byte[] secretTokenBytes;
    private final TokenFingerprint fingerprint;

    Token(TokenDomain domain, String secretTokenString) {
        this.domain = domain;
        this.secretTokenString = secretTokenString;
        this.secretTokenBytes = toUtf8Bytes(secretTokenString);
        this.fingerprint = TokenFingerprint.of(this);
    }

    public static Token of(TokenDomain domain, String secretTokenString) {
        return new Token(domain, secretTokenString);
    }

    public TokenDomain domain() { return domain; }
    public String secretTokenString() { return secretTokenString; }
    public TokenFingerprint fingerprint() { return fingerprint; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        // We assume that domain+fingerprint suffices for equality check.
        // If underlying secret bytes checking is added it MUST use SideChannelSafe.arraysEqual()
        // to avoid leaking secret data via timing side-channels.
        return Objects.equals(domain, token.domain) &&
                Objects.equals(fingerprint, token.fingerprint);
    }

    // Important: actual secret bytes must NOT be part of hashCode calculation, as that risks
    // leaking parts of the secret to an attacker that can influence and observe side effects
    // of the hash code.
    @Override
    public int hashCode() {
        return Objects.hash(domain, fingerprint);
    }

    @Override
    public String toString() {
        // Avoid leaking raw token secret as part of toString() output
        // Fingerprint first, since that's the most important bit.
        return "Token(fingerprint: %s, domain: %s)".formatted(fingerprint, domain);
    }

    /**
     * Token derivations are created by invoking a HKDF (using HMAC-SHA256) that expands the
     * original token secret to the provided number of bytes and the provided domain separation
     * context. The same source token secret will result in different derivations when
     * different contexts are used, but will always generate a deterministic result for the
     * same token+#bytes+context combination.
     */
    byte[] toDerivedBytes(int nHashBytes, byte[] domainSeparationContext) {
        var hkdf = HKDF.unsaltedExtractedFrom(secretTokenBytes);
        return hkdf.expand(nHashBytes, domainSeparationContext);
    }

}
