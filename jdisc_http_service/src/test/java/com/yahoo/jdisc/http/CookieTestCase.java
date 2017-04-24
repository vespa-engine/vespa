// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.DefaultCookie;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNotSame;
import static org.testng.AssertJUnit.assertSame;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class CookieTestCase {

    @Test
    public void requireThatDefaultValuesAreSane() {
        assertCookie(new DefaultCookie("foo", "bar"), new Cookie().setName("foo").setValue("bar"));
        assertCookie(new DefaultCookie("foo", "bar"), new Cookie("foo", "bar"));
    }

    @Test
    public void requireThatAccessorsWork() {
        final Cookie cookie = new Cookie();
        cookie.setName("foo");
        assertEquals("foo", cookie.getName());
        cookie.setName("bar");
        assertEquals("bar", cookie.getName());

        cookie.setValue("foo");
        assertEquals("foo", cookie.getValue());
        cookie.setValue("bar");
        assertEquals("bar", cookie.getValue());

        cookie.setDomain("foo");
        assertEquals("foo", cookie.getDomain());
        cookie.setDomain("bar");
        assertEquals("bar", cookie.getDomain());

        cookie.setPath("foo");
        assertEquals("foo", cookie.getPath());
        cookie.setPath("bar");
        assertEquals("bar", cookie.getPath());

        cookie.setComment("foo");
        assertEquals("foo", cookie.getComment());
        cookie.setComment("bar");
        assertEquals("bar", cookie.getComment());

        cookie.setCommentUrl("foo");
        assertEquals("foo", cookie.getCommentUrl());
        assertSame(cookie.getCommentUrl(), cookie.getCommentURL());
        cookie.setCommentUrl("bar");
        assertEquals("bar", cookie.getCommentUrl());
        assertSame(cookie.getCommentUrl(), cookie.getCommentURL());

        cookie.setMaxAge(69, TimeUnit.DAYS);
        assertEquals(69, cookie.getMaxAge(TimeUnit.DAYS));
        assertEquals(TimeUnit.DAYS.toHours(69), cookie.getMaxAge(TimeUnit.HOURS));
        cookie.setVersion(69);
        assertEquals(69, cookie.getVersion());

        cookie.setSecure(true);
        assertTrue(cookie.isSecure());
        cookie.setSecure(false);
        assertFalse(cookie.isSecure());

        cookie.setHttpOnly(true);
        assertTrue(cookie.isHttpOnly());
        cookie.setHttpOnly(false);
        assertFalse(cookie.isHttpOnly());

        cookie.setDiscard(true);
        assertTrue(cookie.isDiscard());
        cookie.setDiscard(false);
        assertFalse(cookie.isDiscard());

        cookie.ports().add(6);
        assertEquals(1, cookie.ports().size());
        assertTrue(cookie.ports().contains(6));
        cookie.ports().add(9);
        assertEquals(2, cookie.ports().size());
        assertTrue(cookie.ports().contains(6));
        assertTrue(cookie.ports().contains(9));
    }

    @Test
    public void requireThatCopyConstructorWorks() {
        final Cookie lhs = newCookie("foo");
        final Cookie rhs = new Cookie(lhs);
        assertEquals(rhs.getName(), rhs.getName());
        assertEquals(rhs.getValue(), rhs.getValue());
        assertEquals(rhs.getDomain(), rhs.getDomain());
        assertEquals(rhs.getPath(), rhs.getPath());
        assertEquals(rhs.getComment(), rhs.getComment());
        assertEquals(rhs.getCommentUrl(), rhs.getCommentUrl());
        assertEquals(rhs.getMaxAge(TimeUnit.MILLISECONDS), rhs.getMaxAge(TimeUnit.MILLISECONDS));
        assertEquals(rhs.getVersion(), rhs.getVersion());
        assertEquals(rhs.isSecure(), rhs.isSecure());
        assertEquals(rhs.isHttpOnly(), rhs.isHttpOnly());
        assertEquals(rhs.isDiscard(), rhs.isDiscard());
        assertEquals(rhs.ports(), lhs.ports());
        assertNotSame(rhs.ports(), lhs.ports());
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        final Cookie cookie = newCookie("foo");
        assertFalse(cookie.hashCode() == new Cookie().hashCode());
        assertEquals(cookie.hashCode(), cookie.hashCode());
        assertEquals(cookie.hashCode(), new Cookie(cookie).hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        final Cookie cookie = newCookie("foo");
        assertFalse(cookie.equals(new Cookie()));
        assertEquals(cookie, cookie);
        assertEquals(cookie, new Cookie(cookie));
    }

    @Test
    public void requireThatCookieCanBeEncoded() {
        assertEncodeCookie(
                Collections.singletonList("$Version=1; foo.name=foo.value; $Path=path; $Domain=domain; $Port=\"69\""),
                Collections.singletonList(newCookie("foo")));
        assertEncodeCookie(
                Arrays.asList("$Version=1; bar.name=bar.value; $Path=path; $Domain=domain; $Port=\"69\"",
                              "$Version=1; foo.name=foo.value; $Path=path; $Domain=domain; $Port=\"69\""),
                Arrays.asList(newCookie("foo"), newCookie("bar")));
    }

    @Test
    public void requireThatSetCookieCanBeEncoded() {
        assertEncodeSetCookie(
                Collections.singletonList("foo.name=foo.value; Max-Age=0; Path=path; Domain=domain; Secure; " +
                                          "HTTPOnly; Comment=comment; Version=1; CommentURL=\"commentUrl\"; " +
                                          "Port=\"69\"; Discard"),
                Collections.singletonList(newCookie("foo")));
    }

    @Test
    public void requireThatOnlyOneSetCookieCanBeEncoded() {
        try {
            Cookie.toSetCookieHeader(Arrays.asList(newCookie("foo"), newCookie("bar")));
            fail();
        } catch (final IllegalStateException ignored) {

        }
    }

    @Test
    public void requireThatCookieCanBeDecoded() {
        final Cookie foo = new Cookie();
        foo.setName("foo.name");
        foo.setValue("foo.value");
        foo.setVersion(1);
        foo.setPath("path");
        foo.setDomain("domain");
        foo.setMaxAge(-1, TimeUnit.SECONDS);
        assertDecodeSetCookie(Collections.singletonList(foo),
                              "$Version=1;foo.name=foo.value;$Path=path;$Domain=domain;$Port=\"69\"");

        final Cookie bar = new Cookie();
        bar.setName("bar.name");
        bar.setValue("bar.value");
        bar.setVersion(1);
        bar.setPath("path");
        bar.setDomain("domain");
        bar.setMaxAge(-1, TimeUnit.SECONDS);
        assertDecodeCookie(Arrays.asList(foo, bar),
                           "$Version=1;foo.name=foo.value;$Path=path;$Domain=domain;$Port=\"69\";" +
                           "$Version=1;bar.name=bar.value;$Path=path;$Domain=domain;$Port=\"69\";");
    }

    @Test
    public void requireThatSetCookieCanBeDecoded() {
        final Cookie foo = new Cookie();
        foo.setName("foo.name");
        foo.setValue("foo.value");
        foo.setVersion(1);
        foo.setPath("path");
        foo.setDomain("domain");
        foo.setMaxAge(-1, TimeUnit.SECONDS);
        assertDecodeSetCookie(Collections.singletonList(foo),
                              "foo.name=foo.value;Max-Age=0;Path=path;Domain=domain;Secure;HTTPOnly;Comment=comment;" +
                              "Version=2;CommentURL=\"commentUrl\";Port=\"69\";Discard");

        final Cookie bar = new Cookie();
        bar.setName("bar.name");
        bar.setValue("bar.value");
        bar.setVersion(1);
        bar.setPath("path");
        bar.setDomain("domain");
        bar.setMaxAge(-1, TimeUnit.SECONDS);
        assertDecodeSetCookie(Arrays.asList(foo, bar),
                              "bar.name=bar.value;Max-Age=0;Path=path;Domain=domain;Secure;HTTPOnly;Comment=comment;" +
                              "Version=2;CommentURL=\"commentUrl\";Port=\"69\";Discard;" +
                              "foo.name=foo.value;Max-Age=0;Path=path;Domain=domain;Secure;HTTPOnly;Comment=comment;" +
                              "Version=2;CommentURL=\"commentUrl\";Port=\"69\";Discard");
    }

    @Test
    public void requireThatCookieDecoderWorksForGenericValidCookies() {
        new CookieDecoder().decode("Y=v=1&n=8es5opih9ljtk&l=og0_iedeh0qqvqqr/o&p=m2g2rs6012000000&r=pv&lg=en-US&intl=" +
                                   "us&np=1; T=z=h.nzPBhSP4PBVd5JqacVnIbNjU1NAY2TjYzNzVOTjYzNzM0Mj&a=YAE&sk=DAALShmNQ" +
                                   "vhoZV&ks=EAABsibvMK6ejwn0uUoS4rC9w--~E&d=c2wBTVRJeU13RXhPVEUwTURJNU9URTBNRFF6TlRJ" +
                                   "NU5nLS0BYQFZQUUBZwE1VkNHT0w3VUVDTklJVEdRR1FXT0pOSkhEQQFzY2lkAWNOUnZIbEc3ZHZoVHlWZ" +
                                   "0NoXzEwYkxhOVdzcy0Bb2sBWlcwLQF0aXABWUhwTmVDAXp6AWgubnpQQkE3RQ--");
    }

    @Test
    public void requireThatCookieDecoderWorksForYInvalidCookies() {
        new CookieDecoder().decode("Y=v=1&n=77nkr5t7o4nqn&l=og0_iedeh0qqvqqr/o&p=m2g2rs6012000000&r=pv&lg=en-US&intl=" +
                                   "us&np=1; T=z=05nzPB0NP4PBN/n0gwc1AWGNjU1NAY2TjYzNzVOTjYzNzM0Mj&a=QAE&sk=DAA4R2svo" +
                                   "osjIa&ks=EAAj3nBQFkN4ZmuhqFxJdNoaQ--~E&d=c2wBTVRJeU13RXhPVEUwTURJNU9URTBNRFF6TlRJ" +
                                   "NU5nLS0BYQFRQUUBZwE1VkNHT0w3VUVDTklJVEdRR1FXT0pOSkhEQQFzY2lkAUpPalRXOEVsUDZrR3RHT" +
                                   "VZkX29CWk53clJIQS0BdGlwAVlIcE5lQwF6egEwNW56UEJBN0U-");
    }

    @Test
    public void requireThatCookieDecoderWorksForYValidCookies() {
        new CookieDecoder().decode("Y=v=1&n=3767k6te5aj2s&l=1v4u3001uw2ys00q0rw0qrw34q0x5s3u/o&p=030vvit012000000&iz=" +
                                   "&r=pu&lg=en-US,it-IT,it&intl=it&np=1; T=z=m38yPBmLk3PBWvehTPBhBHYNU5OBjQ3NE5ONU5P" +
                                   "NDY0NzU0M0&a=IAE&sk=DAAAx5URYgbhQ6&ks=EAA4rTgdlAGeMQmdYeM_VehGg--~E&d=c2wBTWprNUF" +
                                   "UTXdNems1TWprNE16RXpNREl6TkRneAFhAUlBRQFnAUVJSlNMSzVRM1pWNVNLQVBNRkszQTRaWDZBAXNj" +
                                   "aWQBSUlyZW5paXp4NS4zTUZMMDVlSVhuMjZKYUcwLQFvawFaVzAtAWFsAW1hcmlvYXByZWFAeW1haWwuY" +
                                   "29tAXp6AW0zOHlQQkE3RQF0aXABaXRZOFRE");
    }

    @Test
    public void requireThatCookieDecoderWorksForGenericInvalidCookies() {
        new CookieDecoder().decode("Y=v=1&n=e92s5cq8qbs6h&l=3kdb0f.3@i126be10b.d4j/o&p=m1f2qgmb13000107&r=g5&lg=en-US" +
                                   "&intl=us; T=z=TXp3OBTrQ8OBFMcj3GBpFSyNk83TgY2MjMwN04zMDMw&a=YAE&sk=DAAVfaNwLeISrX" +
                                   "&ks=EAAOeNNgY8c5hV8YzPYmnrW7w--~E&d=c2wBTVRnd09RRXhOVFEzTURrME56UTMBYQFZQUUBZwFMQ" +
                                   "U5NT0Q2UjY2Q0I1STY0R0tKSUdVQVlRRQFvawFaVzAtAXRpcAFMTlRUdkMBenoBVFhwM09CQTdF&af=QU" +
                                   "FBQ0FDQURBd0FCMUNCOUFJQUJBQ0FEQU1IME1nTWhNbiZ0cz0xMzIzMjEwMTk1JnBzPVA1d3NYakh0aVk" +
                                   "2UDMuUGZ6WkdTT2ctLQ--");
    }

    private static void assertDecodeCookie(final List<Cookie> expected, final String toDecode) {
        assertCookies(expected, Cookie.fromCookieHeader(toDecode));
    }

    private static void assertDecodeSetCookie(final List<Cookie> expected, final String toDecode) {
        assertCookies(expected, Cookie.fromSetCookieHeader(toDecode));
    }

    private static void assertCookies(final List<Cookie> expected, final List<Cookie> actual) {
        assertEquals(expected.size(), actual.size());
        for (final Cookie cookie : expected) {
            assertNotNull(actual.remove(cookie));
        }
    }

    private static void assertEncodeCookie(final List<String> expected, final List<Cookie> toEncode) {
        assertCookies(expected, Cookie.toCookieHeader(toEncode));
    }

    private static void assertEncodeSetCookie(final List<String> expected, final List<Cookie> toEncode) {
        assertCookies(expected, Cookie.toSetCookieHeader(toEncode));
    }

    private static void assertCookies(final List<String> expected, final String actual) {
        final Set<Integer> seen = new HashSet<>();
        for (final String str : expected) {
            final int pos = actual.indexOf(str);
            assertTrue(pos >= 0);
            assertTrue(seen.add(pos));
        }
    }

    private static void assertCookie(final DefaultCookie expected, final Cookie actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getValue(), actual.getValue());
        assertEquals(expected.getDomain(), actual.getDomain());
        assertEquals(expected.getPath(), actual.getPath());
        assertEquals(expected.getComment(), actual.getComment());
        assertEquals(expected.getCommentUrl(), actual.getCommentUrl());
        assertEquals(expected.getMaxAge(), actual.getMaxAge(TimeUnit.SECONDS));
        assertEquals(expected.getVersion(), actual.getVersion());
        assertEquals(expected.isSecure(), actual.isSecure());
        assertEquals(expected.isHttpOnly(), actual.isHttpOnly());
        assertEquals(expected.isDiscard(), actual.isDiscard());
    }

    private static Cookie newCookie(final String name) {
        final Cookie cookie = new Cookie();
        cookie.setName(name + ".name");
        cookie.setValue(name + ".value");
        cookie.setDomain("domain");
        cookie.setPath("path");
        cookie.setComment("comment");
        cookie.setCommentUrl("commentUrl");
        cookie.setMaxAge(69, TimeUnit.MILLISECONDS);
        cookie.setVersion(2);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setDiscard(true);
        cookie.ports().add(69);
        return cookie;
    }
}
