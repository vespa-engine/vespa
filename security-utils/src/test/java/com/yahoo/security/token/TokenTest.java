// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.token;

import org.junit.jupiter.api.Test;

import static com.yahoo.security.ArrayUtils.toUtf8Bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenTest {

    private static final TokenDomain TEST_DOMAIN = TokenDomain.of("my check hash");

    @Test
    void tokens_are_equality_comparable() {
        var td1 = TokenDomain.of("hash 1");
        var td2 = TokenDomain.of("hash 2");

        var td1_t1 = Token.of(td1, "foo");
        var td1_t2 = Token.of(td1, "foo");
        var td1_t3 = Token.of(td1, "bar");
        var td2_t1 = Token.of(td2, "foo");
        // Tokens in same domain with same content are equal
        assertEquals(td1_t1, td1_t2);
        // Tokens in same domain with different content are not equal
        assertNotEquals(td1_t1, td1_t3);
        // Tokens in different domains are not considered equal
        assertNotEquals(td1_t1, td2_t1);
    }

    @Test
    void check_hashes_are_equality_comparable() {
        var h1 = TokenCheckHash.ofRawBytes(toUtf8Bytes("foo"));
        var h2 = TokenCheckHash.ofRawBytes(toUtf8Bytes("foo"));
        var h3 = TokenCheckHash.ofRawBytes(toUtf8Bytes("bar"));
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
    }

    @Test
    void token_generator_generates_new_tokens() {
        var t1 = TokenGenerator.generateToken(TEST_DOMAIN, "foo_", 16);
        var t2 = TokenGenerator.generateToken(TEST_DOMAIN, "foo_", 16);
        // The space of possible generated tokens is effectively infinite, so we'll
        // pragmatically round down infinity to 2...!
        assertNotEquals(t1, t2);
        assertTrue(t1.secretTokenString().startsWith("foo_"));
        assertTrue(t2.secretTokenString().startsWith("foo_"));
        // Token sizes are always greater than their raw binary size due to base62-encoding
        assertTrue(t1.secretTokenString().length() > 20);
        assertTrue(t2.secretTokenString().length() > 20);
    }

    @Test
    void token_fingerprint_considers_entire_token_string() {
        var td = TokenDomain.of("my check hash");
        var t1 = Token.of(td, "kittens_123456789");
        var t2 = Token.of(td, "puppies_123456789");
        var t3 = Token.of(td, "puppies_1234567890");
        assertEquals("4c7e222f923b459e19eae17f8d565fe8", t1.fingerprint().toHexString());
        assertEquals("59796927954fdd710c483177cb6e2b27", t2.fingerprint().toHexString());
        assertEquals("daae1353ab47e0fe9244d3c5a7c31724", t3.fingerprint().toHexString());
    }

    @Test
    void token_fingerprint_is_independent_of_token_domain() {
        var td = TokenDomain.of("my check hash 2");
        var t = Token.of(td, "kittens_123456789");
        // Same as with domain 'my check hash'
        assertEquals("4c7e222f923b459e19eae17f8d565fe8", t.fingerprint().toHexString());
    }

    @Test
    void fingerprint_is_printed_with_delimiters_by_default() {
        var t = Token.of(TEST_DOMAIN, "bar");
        var fp = t.fingerprint();
        assertEquals("3f:2a:3b:64:09:74:c6:56:45:72:d2:a0:66:b8:60:b8", fp.toString());
    }

    @Test
    void token_check_hash_differs_from_fingerprint() { // ... with extremely high probability
        var t = Token.of(TEST_DOMAIN, "foo");
        var fp = t.fingerprint();
        // Generate check-hashes with the same length as fingerprints.
        // If we generate with different lengths, hashes will differ by definition, but that wouldn't
        // really tell us anything about whether the hashes are actually derived differently.
        var hash = TokenCheckHash.of(t, TokenFingerprint.FINGERPRINT_BYTES);
        assertEquals("ee01a269abe96ffffe25f49a6be08189", fp.toHexString());
        assertEquals("f0f56b46df55f73eccb9409c203b02c7", hash.toHexString());
    }

    @Test
    void different_check_hash_domains_give_different_outputs() {
        var d1 = TokenDomain.of("domain: 1");
        var d2 = TokenDomain.of("domain: 2");
        var d3 = TokenDomain.of("domain: 3");
        assertEquals("cc0c504b52bfd9b0a9cdb1651c0f3515", TokenCheckHash.of(Token.of(d1, "foo"), 16).toHexString());
        assertEquals("a27c7fc350699c71bc456a86bd571479", TokenCheckHash.of(Token.of(d2, "foo"), 16).toHexString());
        assertEquals("119cc7046689e6de796fd4005aaab6dc", TokenCheckHash.of(Token.of(d3, "foo"), 16).toHexString());
    }

    @Test
    void token_stringification_does_not_contain_raw_secret() {
        var t = Token.of(TEST_DOMAIN, "foo");
        assertEquals("Token(fingerprint: ee:01:a2:69:ab:e9:6f:ff:fe:25:f4:9a:6b:e0:81:89, " +
                           "domain: 'my check hash')",
                     t.toString());
    }

    @Test
    void token_fingerprints_and_check_hashes_are_stable() {
        var d1 = TokenDomain.of("domain: 1");
        var d2 = TokenDomain.of("domain: 2");

        var t1 = Token.of(d1, "my_token_1");
        assertEquals("3117c3a6e5481f08fc51233da828efe8", t1.fingerprint().toHexString());
        assertEquals("3117c3a6e5481f08fc51233da828efe8", TokenFingerprint.of(t1).toHexString());
        var t1_h1 = TokenCheckHash.of(t1, 32);
        var t1_h2 = TokenCheckHash.of(t1, 16);
        assertEquals("65da02dbed156442d85c93caf930217488916082936d17fef29137dc12110062", t1_h1.toHexString());
        assertEquals("65da02dbed156442d85c93caf9302174", t1_h2.toHexString()); // same prefix, just truncated

        var t2 = Token.of(d1, "my_token_2");
        assertEquals("1bfe7e2e81e8fa10aaabdd19f151be23", t2.fingerprint().toHexString());
        var t2_h = TokenCheckHash.of(t2, 32);
        assertEquals("8f3695492c3fd977b44067580ad57e87883317973e7c09cd859666da8edbd42f", t2_h.toHexString());

        var t3 = Token.of(d2, "my_token_1"); // Different domain
        assertEquals("3117c3a6e5481f08fc51233da828efe8", t3.fingerprint().toHexString());
        var t3_h = TokenCheckHash.of(t3, 32);
        assertEquals("f566dbec641aa64723dd19124afe6c96a821638f9b59f46bbe14f61c3704b32a", t3_h.toHexString());
    }

    @Test
    void token_fingerprints_and_check_hashes_can_be_constructed_from_hex() {
        var domain = TokenDomain.of("check domain");
        var token = Token.of(domain, "my_token");

        var fingerprintHex = token.fingerprint().toHexString();
        assertEquals(token.fingerprint(), TokenFingerprint.ofHex(fingerprintHex));

        var fingerprintDelimitedHex = token.fingerprint().toDelimitedHexString();
        assertEquals(token.fingerprint(), TokenFingerprint.ofHex(fingerprintDelimitedHex));

        var checkHash = TokenCheckHash.of(token, 32);
        var checkHashHex = checkHash.toHexString();
        assertEquals(checkHash, TokenCheckHash.ofHex(checkHashHex));
    }

    @Test
    void cannot_create_token_domains_equal_to_fingerprint_context() {
        assertThrows(IllegalArgumentException.class, () -> new TokenDomain(TokenFingerprint.FINGERPRINT_CONTEXT));
    }

}
