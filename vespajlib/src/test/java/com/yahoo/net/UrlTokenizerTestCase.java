// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * @author Simon Thoresen Hult
 */
public class UrlTokenizerTestCase {

    @Test
    public void requireThatAllTokenCharactersAreAccepted() {
        assertTerms("a", "a");
        assertTerms("aa", "aa");
        assertTerms("aaa", "aaa");
        for (int c = Character.MIN_VALUE; c < Character.MAX_VALUE; ++c) {
            if (c == '%') {
                continue; // escape
            }
            String img = String.format("a%ca", c);
            if ((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c == '-' || c == '_'))
            {
                assertTerms(img, toLowerCase(img));
            } else {
                assertTerms(img, "a", "a");
            }
        }
    }

    @Test
    public void requireThatUrlCanBeTokenized() {
        assertTokenize("",
                       new UrlToken(UrlToken.Type.SCHEME, 0, null, "http"),
                       new UrlToken(UrlToken.Type.PORT, 0, null, "80"));
        assertTokenize("scheme:",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"));
        assertTokenize("scheme://host",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.HOST, 9, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 9, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 13, null, UrlTokenizer.TERM_ENDHOST));
        assertTokenize("scheme://user@host",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.HOST, 14, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 14, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 18, null, UrlTokenizer.TERM_ENDHOST));
        assertTokenize("scheme://user:pass@host",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST));
        assertTokenize("scheme://user:pass@host:69",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "69", "69"));
        assertTokenize("scheme://user:pass@host:69/path",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 27, "path", "path"));
        assertTokenize("scheme://user:pass@host:69/path?query",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 27, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 32, "query", "query"));
        assertTokenize("scheme://user:pass@host:69/path?query#fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 27, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 32, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 38, "fragment", "fragment"));
    }

    @Test
    public void requireThatComponentsCanHaveMultipleTokens() {
        assertTokenize("sch+eme://us+er:pa+ss@ho+st:69/pa/th?que+ry#frag+ment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "sch", "sch"),
                       new UrlToken(UrlToken.Type.SCHEME, 4, "eme", "eme"),
                       new UrlToken(UrlToken.Type.USERINFO, 10, "us", "us"),
                       new UrlToken(UrlToken.Type.USERINFO, 13, "er", "er"),
                       new UrlToken(UrlToken.Type.PASSWORD, 16, "pa", "pa"),
                       new UrlToken(UrlToken.Type.PASSWORD, 19, "ss", "ss"),
                       new UrlToken(UrlToken.Type.HOST, 22, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 22, "ho", "ho"),
                       new UrlToken(UrlToken.Type.HOST, 25, "st", "st"),
                       new UrlToken(UrlToken.Type.HOST, 27, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 28, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 31, "pa", "pa"),
                       new UrlToken(UrlToken.Type.PATH, 34, "th", "th"),
                       new UrlToken(UrlToken.Type.QUERY, 37, "que", "que"),
                       new UrlToken(UrlToken.Type.QUERY, 41, "ry", "ry"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 44, "frag", "frag"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 49, "ment", "ment"));
    }
    @Test
    public void requireThatSequencesOfDelimitersAreCollapsed() {
        assertTokenize("sch++eme://us++er:pa++ss@ho++st:69/pa/th?que++ry#frag++ment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "sch", "sch"),
                       new UrlToken(UrlToken.Type.SCHEME, 5, "eme", "eme"),
                       new UrlToken(UrlToken.Type.USERINFO, 11, "us", "us"),
                       new UrlToken(UrlToken.Type.USERINFO, 15, "er", "er"),
                       new UrlToken(UrlToken.Type.PASSWORD, 18, "pa", "pa"),
                       new UrlToken(UrlToken.Type.PASSWORD, 22, "ss", "ss"),
                       new UrlToken(UrlToken.Type.HOST, 25, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 25, "ho", "ho"),
                       new UrlToken(UrlToken.Type.HOST, 29, "st", "st"),
                       new UrlToken(UrlToken.Type.HOST, 31, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 32, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 35, "pa", "pa"),
                       new UrlToken(UrlToken.Type.PATH, 38, "th", "th"),
                       new UrlToken(UrlToken.Type.QUERY, 41, "que", "que"),
                       new UrlToken(UrlToken.Type.QUERY, 46, "ry", "ry"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 49, "frag", "frag"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 55, "ment", "ment"));
    }

    @Test
    public void requireThatIPv6CanBeTokenized() {
        assertTokenize("scheme://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.HOST, 10, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 10, "2001", "2001"),
                       new UrlToken(UrlToken.Type.HOST, 15, "0db8", "0db8"),
                       new UrlToken(UrlToken.Type.HOST, 20, "85a3", "85a3"),
                       new UrlToken(UrlToken.Type.HOST, 25, "0000", "0000"),
                       new UrlToken(UrlToken.Type.HOST, 30, "0000", "0000"),
                       new UrlToken(UrlToken.Type.HOST, 35, "8a2e", "8a2e"),
                       new UrlToken(UrlToken.Type.HOST, 40, "0370", "0370"),
                       new UrlToken(UrlToken.Type.HOST, 45, "7334", "7334"),
                       new UrlToken(UrlToken.Type.HOST, 49, null, UrlTokenizer.TERM_ENDHOST));
    }

    @Test
    public void requireThatTermsAreLowerCased() {
        assertTokenize("SCHEME://USER:PASS@HOST:69/PATH?QUERY#FRAGMENT",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "SCHEME", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "USER", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "PASS", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "HOST", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 27, "PATH", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 32, "QUERY", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 38, "FRAGMENT", "fragment"));
    }

    @Test
    public void requireThatEscapedCharsAreDecoded() {
        assertTokenize("sch%65me://%75ser:p%61ss@h%6fst:69/p%61th?q%75ery#fr%61gment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "sch%65me", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 11, "%75ser", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 18, "p%61ss", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 25, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 25, "h%6fst", "host"),
                       new UrlToken(UrlToken.Type.HOST, 31, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 32, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 35, "p%61th", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 42, "q%75ery", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 50, "fr%61gment", "fragment"));
    }

    @Test
    public void requireThatDecodedCharsAreLowerCased() {
        assertTokenize("sch%45me://%55ser:p%41ss@h%4fst:69/p%41th?q%55ery#fr%41gment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "sch%45me", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 11, "%55ser", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 18, "p%41ss", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 25, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 25, "h%4fst", "host"),
                       new UrlToken(UrlToken.Type.HOST, 31, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 32, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 35, "p%41th", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 42, "q%55ery", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 50, "fr%41gment", "fragment"));
    }

    @Test
    public void requireThatDecodedCharsCanSplitTokens() {
        assertTokenize("sch%2beme://us%2ber:pa%2bss@ho%2bst:69/pa/th?que%2bry#frag%2bment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "sch", "sch"),
                       new UrlToken(UrlToken.Type.SCHEME, 6, "eme", "eme"),
                       new UrlToken(UrlToken.Type.USERINFO, 12, "us", "us"),
                       new UrlToken(UrlToken.Type.USERINFO, 17, "er", "er"),
                       new UrlToken(UrlToken.Type.PASSWORD, 20, "pa", "pa"),
                       new UrlToken(UrlToken.Type.PASSWORD, 25, "ss", "ss"),
                       new UrlToken(UrlToken.Type.HOST, 28, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 28, "ho", "ho"),
                       new UrlToken(UrlToken.Type.HOST, 33, "st", "st"),
                       new UrlToken(UrlToken.Type.HOST, 35, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 36, "69", "69"),
                       new UrlToken(UrlToken.Type.PATH, 39, "pa", "pa"),
                       new UrlToken(UrlToken.Type.PATH, 42, "th", "th"),
                       new UrlToken(UrlToken.Type.QUERY, 45, "que", "que"),
                       new UrlToken(UrlToken.Type.QUERY, 51, "ry", "ry"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 54, "frag", "frag"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 61, "ment", "ment"));
    }

    @Test
    public void requireThatSchemeCanBeGuessed() {
        assertTokenize("//host:80",
                       new UrlToken(UrlToken.Type.SCHEME, 0, null, "http"),
                       new UrlToken(UrlToken.Type.HOST, 2, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 2, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 6, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 7, "80", "80"));
    }

    @Test
    public void requireThatHostCanBeGuessed() {
        assertTokenize("file:/path",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "file", "file"),
                       new UrlToken(UrlToken.Type.HOST, 4, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 4, null, "localhost"),
                       new UrlToken(UrlToken.Type.HOST, 4, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PATH, 6, "path", "path"));
    }

    @Test
    public void requireThatPortCanBeGuessed() {
        assertTokenize("http://host",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "http", "http"),
                       new UrlToken(UrlToken.Type.HOST, 7, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 7, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 11, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 11, null, "80"));
    }

    @Test
    public void requireThatComponentsAreOptional() {
        assertTokenize("scheme", "user", "pass", "host", 99, "/path", "query", "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "99", "99"),
                       new UrlToken(UrlToken.Type.PATH, 27, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 32, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 38, "fragment", "fragment"));
        assertTokenize(null, "user", "pass", "host", 99, "/path", "query", "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, null, "http"),
                       new UrlToken(UrlToken.Type.USERINFO, 2, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 7, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 12, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 12, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 16, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 17, "99", "99"),
                       new UrlToken(UrlToken.Type.PATH, 20, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 25, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 31, "fragment", "fragment"));
        assertTokenize("scheme", null, "pass", "host", 99, "/path", "query", "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.PASSWORD, 10, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 15, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 15, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 20, "99", "99"),
                       new UrlToken(UrlToken.Type.PATH, 23, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 28, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 34, "fragment", "fragment"));
        assertTokenize("scheme", null, null, "host", 99, "/path", "query", "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.HOST, 9, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 9, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 13, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 14, "99", "99"),
                       new UrlToken(UrlToken.Type.PATH, 17, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 22, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 28, "fragment", "fragment"));
        assertTokenize("scheme", null, null, null, 99, "/path", "query", "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.PORT, 8, "99", "99"),
                       new UrlToken(UrlToken.Type.PATH, 11, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 16, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 22, "fragment", "fragment"));
        assertTokenize("scheme", "user", "pass", "host", null, "/path", "query", "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PATH, 24, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 29, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 35, "fragment", "fragment"));
        assertTokenize("scheme", "user", "pass", "host", 99, null, "query", "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "99", "99"),
                       new UrlToken(UrlToken.Type.QUERY, 27, "query", "query"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 33, "fragment", "fragment"));
        assertTokenize("scheme", "user", "pass", "host", 99, "/path", null, "fragment",
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "99", "99"),
                       new UrlToken(UrlToken.Type.PATH, 27, "path", "path"),
                       new UrlToken(UrlToken.Type.FRAGMENT, 32, "fragment", "fragment"));
        assertTokenize("scheme", "user", "pass", "host", 99, "/path", "query", null,
                       new UrlToken(UrlToken.Type.SCHEME, 0, "scheme", "scheme"),
                       new UrlToken(UrlToken.Type.USERINFO, 9, "user", "user"),
                       new UrlToken(UrlToken.Type.PASSWORD, 14, "pass", "pass"),
                       new UrlToken(UrlToken.Type.HOST, 19, null, UrlTokenizer.TERM_STARTHOST),
                       new UrlToken(UrlToken.Type.HOST, 19, "host", "host"),
                       new UrlToken(UrlToken.Type.HOST, 23, null, UrlTokenizer.TERM_ENDHOST),
                       new UrlToken(UrlToken.Type.PORT, 24, "99", "99"),
                       new UrlToken(UrlToken.Type.PATH, 27, "path", "path"),
                       new UrlToken(UrlToken.Type.QUERY, 32, "query", "query"));
    }

    private static void assertTokenize(String scheme, String userInfo, String password, String host, Integer port,
                                       String path, String query, String fragment, UrlToken... expected)
    {
        assertTokenize(new Url(scheme, userInfo, password, host, port, path, query, fragment), expected);
    }

    private static void assertTokenize(String url, UrlToken... expected) {
        assertTokenize(Url.fromString(url), expected);
    }

    private static void assertTokenize(Url url, UrlToken... expected) {
        Iterator<UrlToken> expectedIt = Arrays.asList(expected).iterator();
        Iterator<UrlToken> actualIt = new UrlTokenizer(url).tokenize().iterator();
        while (expectedIt.hasNext()) {
            assertTrue(actualIt.hasNext());
            assertEquals(expectedIt.next(), actualIt.next());
        }
        assertFalse(expectedIt.hasNext());
        assertFalse(actualIt.hasNext());
    }

    private static void assertTerms(String img, String... expected) {
        List<UrlToken> actual = new LinkedList<>();
        UrlTokenizer.addTokens(actual, UrlToken.Type.PATH, 0, img, true);

        Iterator<String> expectedIt = Arrays.asList(expected).iterator();
        Iterator<UrlToken> actualIt = actual.iterator();
        while (expectedIt.hasNext()) {
            assertTrue(actualIt.hasNext());
            assertEquals(expectedIt.next(), actualIt.next().getTerm());
        }
        assertFalse(expectedIt.hasNext());
        assertFalse(actualIt.hasNext());
    }
}
