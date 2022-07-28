// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public class CookieTestCase {

    @Test
    void requireThatDefaultValuesAreSane() {
        Cookie cookie = new Cookie("foo", "bar");
        assertEquals("foo", cookie.getName());
        assertEquals("bar", cookie.getValue());
        assertNull(cookie.getDomain());
        assertEquals(Integer.MIN_VALUE, cookie.getMaxAge(TimeUnit.SECONDS));
        assertNull(cookie.getPath());
        assertEquals(false, cookie.isHttpOnly());
        assertEquals(false, cookie.isSecure());
    }

    @Test
    void requireThatAccessorsWork() {
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

        cookie.setMaxAge(69, TimeUnit.DAYS);
        assertEquals(69, cookie.getMaxAge(TimeUnit.DAYS));
        assertEquals(TimeUnit.DAYS.toHours(69), cookie.getMaxAge(TimeUnit.HOURS));

        cookie.setSecure(true);
        assertTrue(cookie.isSecure());
        cookie.setSecure(false);
        assertFalse(cookie.isSecure());

        cookie.setHttpOnly(true);
        assertTrue(cookie.isHttpOnly());
        cookie.setHttpOnly(false);
        assertFalse(cookie.isHttpOnly());
    }

    @Test
    void requireThatCopyConstructorWorks() {
        final Cookie lhs = newSetCookie("foo");
        final Cookie rhs = new Cookie(lhs);
        assertEquals(rhs.getName(), rhs.getName());
        assertEquals(rhs.getValue(), rhs.getValue());
        assertEquals(rhs.getDomain(), rhs.getDomain());
        assertEquals(rhs.getPath(), rhs.getPath());
        assertEquals(rhs.getMaxAge(TimeUnit.MILLISECONDS), rhs.getMaxAge(TimeUnit.MILLISECONDS));
        assertEquals(rhs.isSecure(), rhs.isSecure());
        assertEquals(rhs.isHttpOnly(), rhs.isHttpOnly());
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        final Cookie cookie = newCookie("foo");
        assertNotNull(new Cookie().hashCode());
        assertEquals(cookie.hashCode(), cookie.hashCode());
        assertEquals(cookie.hashCode(), new Cookie(cookie).hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        final Cookie cookie = newCookie("foo");
        assertNotEquals(cookie, new Cookie());
        assertEquals(cookie, cookie);
        assertEquals(cookie, new Cookie(cookie));
    }

    @Test
    void requireThatCookieCanBeEncoded() {
        assertEncodeCookie(
                "foo.name=foo.value",
                List.of(newCookie("foo")));
        assertEncodeCookie(
                "foo.name=foo.value;bar.name=bar.value",
                List.of(newCookie("foo"), newCookie("bar")));
    }

    @Test
    void requireThatSetCookieCanBeEncoded() {
        assertEncodeSetCookie(
                List.of("foo.name=foo.value; Path=path; Domain=domain; Secure; HttpOnly",
                        "foo.name=foo.value; Path=path; Domain=domain; Secure; HttpOnly; SameSite=None"),
                List.of(newSetCookie("foo"),
                        newSetCookie("foo").setSameSite(Cookie.SameSite.NONE)));
    }

    @Test
    void requireThatCookieCanBeDecoded() {
        final Cookie foo = new Cookie();
        foo.setName("foo.name");
        foo.setValue("foo.value");
        assertDecodeCookie(List.of(newCookie("foo")), "foo.name=foo.value");

        final Cookie bar = new Cookie();
        bar.setName("bar.name");
        bar.setValue("bar.value");
        assertDecodeCookie(List.of(foo, bar), "foo.name=foo.value; bar.name=bar.value");
    }

    @Test
    void requireThatSetCookieCanBeDecoded() {
        final Cookie foo = new Cookie();
        foo.setName("foo.name");
        foo.setValue("foo.value");
        foo.setPath("path");
        foo.setDomain("domain");
        foo.setMaxAge(0, TimeUnit.SECONDS);
        foo.setSecure(true);
        foo.setHttpOnly(true);
        assertDecodeSetCookie(foo, "foo.name=foo.value;Max-Age=0;Path=path;Domain=domain;Secure;HTTPOnly;");

        final Cookie bar = new Cookie();
        bar.setName("bar.name");
        bar.setValue("bar.value");
        bar.setPath("path");
        bar.setDomain("domain");
        bar.setMaxAge(0, TimeUnit.SECONDS);
        assertDecodeSetCookie(bar, "bar.name=bar.value;Max-Age=0;Path=path;Domain=domain;");
    }

    @Test
    void requireThatCookieDecoderWorksForGenericValidCookies() {
        Cookie.fromCookieHeader("Y=v=1&n=8es5opih9ljtk&l=og0_iedeh0qqvqqr/o&p=m2g2rs6012000000&r=pv&lg=en-US&intl=" +
                "us&np=1; T=z=h.nzPBhSP4PBVd5JqacVnIbNjU1NAY2TjYzNzVOTjYzNzM0Mj&a=YAE&sk=DAALShmNQ" +
                "vhoZV&ks=EAABsibvMK6ejwn0uUoS4rC9w--~E&d=c2wBTVRJeU13RXhPVEUwTURJNU9URTBNRFF6TlRJ" +
                "NU5nLS0BYQFZQUUBZwE1VkNHT0w3VUVDTklJVEdRR1FXT0pOSkhEQQFzY2lkAWNOUnZIbEc3ZHZoVHlWZ" +
                "0NoXzEwYkxhOVdzcy0Bb2sBWlcwLQF0aXABWUhwTmVDAXp6AWgubnpQQkE3RQ--");
    }

    @Test
    void requireThatCookieDecoderWorksForYInvalidCookies() {
        Cookie.fromCookieHeader("Y=v=1&n=77nkr5t7o4nqn&l=og0_iedeh0qqvqqr/o&p=m2g2rs6012000000&r=pv&lg=en-US&intl=" +
                "us&np=1; T=z=05nzPB0NP4PBN/n0gwc1AWGNjU1NAY2TjYzNzVOTjYzNzM0Mj&a=QAE&sk=DAA4R2svo" +
                "osjIa&ks=EAAj3nBQFkN4ZmuhqFxJdNoaQ--~E&d=c2wBTVRJeU13RXhPVEUwTURJNU9URTBNRFF6TlRJ" +
                "NU5nLS0BYQFRQUUBZwE1VkNHT0w3VUVDTklJVEdRR1FXT0pOSkhEQQFzY2lkAUpPalRXOEVsUDZrR3RHT" +
                "VZkX29CWk53clJIQS0BdGlwAVlIcE5lQwF6egEwNW56UEJBN0U-");
    }

    @Test
    void requireThatCookieDecoderWorksForYValidCookies() {
        Cookie.fromCookieHeader("Y=v=1&n=3767k6te5aj2s&l=1v4u3001uw2ys00q0rw0qrw34q0x5s3u/o&p=030vvit012000000&iz=" +
                "&r=pu&lg=en-US,it-IT,it&intl=it&np=1; T=z=m38yPBmLk3PBWvehTPBhBHYNU5OBjQ3NE5ONU5P" +
                "NDY0NzU0M0&a=IAE&sk=DAAAx5URYgbhQ6&ks=EAA4rTgdlAGeMQmdYeM_VehGg--~E&d=c2wBTWprNUF" +
                "UTXdNems1TWprNE16RXpNREl6TkRneAFhAUlBRQFnAUVJSlNMSzVRM1pWNVNLQVBNRkszQTRaWDZBAXNj" +
                "aWQBSUlyZW5paXp4NS4zTUZMMDVlSVhuMjZKYUcwLQFvawFaVzAtAWFsAW1hcmlvYXByZWFAeW1haWwuY" +
                "29tAXp6AW0zOHlQQkE3RQF0aXABaXRZOFRE");
    }

    @Test
    void requireThatCookieDecoderWorksForGenericInvalidCookies() {
        Cookie.fromCookieHeader("Y=v=1&n=e92s5cq8qbs6h&l=3kdb0f.3@i126be10b.d4j/o&p=m1f2qgmb13000107&r=g5&lg=en-US" +
                "&intl=us; T=z=TXp3OBTrQ8OBFMcj3GBpFSyNk83TgY2MjMwN04zMDMw&a=YAE&sk=DAAVfaNwLeISrX" +
                "&ks=EAAOeNNgY8c5hV8YzPYmnrW7w--~E&d=c2wBTVRnd09RRXhOVFEzTURrME56UTMBYQFZQUUBZwFMQ" +
                "U5NT0Q2UjY2Q0I1STY0R0tKSUdVQVlRRQFvawFaVzAtAXRpcAFMTlRUdkMBenoBVFhwM09CQTdF&af=QU" +
                "FBQ0FDQURBd0FCMUNCOUFJQUJBQ0FEQU1IME1nTWhNbiZ0cz0xMzIzMjEwMTk1JnBzPVA1d3NYakh0aVk" +
                "2UDMuUGZ6WkdTT2ctLQ--");
    }

    @Test
    void requireMappingBetweenSameSiteAndJettySameSite() {
        for (var jdiscSameSite : Cookie.SameSite.values()) {
            assertEquals(jdiscSameSite, Cookie.SameSite.fromJettySameSite(jdiscSameSite.jettySameSite()));
        }

        for (var jettySameSite : org.eclipse.jetty.http.HttpCookie.SameSite.values()) {
            assertEquals(jettySameSite, Cookie.SameSite.fromJettySameSite(jettySameSite).jettySameSite());
        }
    }

    private static void assertEncodeCookie(String expectedResult, List<Cookie> cookies) {
        String actual = Cookie.toCookieHeader(cookies);
        String expectedResult1 = expectedResult;
        assertThat(actual, equalTo(expectedResult1));
    }

    private static void assertEncodeSetCookie(List<String> expectedResult, List<Cookie> cookies) {
        assertThat(Cookie.toSetCookieHeaders(cookies), containsInAnyOrder(expectedResult.toArray()));
    }

    private static void assertDecodeCookie(List<Cookie> expected, String toDecode) {
        assertThat(Cookie.fromCookieHeader(toDecode), containsInAnyOrder(expected.toArray()));
    }

    private static void assertDecodeSetCookie(final Cookie expected, String toDecode) {
        assertThat(Cookie.fromSetCookieHeader(toDecode), equalTo(expected));
    }

    private static Cookie newCookie(final String name) {
        final Cookie cookie = new Cookie();
        cookie.setName(name + ".name");
        cookie.setValue(name + ".value");
        return cookie;
    }

    private static Cookie newSetCookie(String name) {
        final Cookie cookie = new Cookie();
        cookie.setName(name + ".name");
        cookie.setValue(name + ".value");
        cookie.setDomain("domain");
        cookie.setPath("path");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        return cookie;
    }
}
