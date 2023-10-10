// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.Cookies;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A RFC 6265 compliant cookie.
 *
 * Note: RFC 2109 and RFC 2965 is no longer supported. All fields that are not part of RFC 6265 are deprecated.
 *
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class Cookie {

    private final Set<Integer> ports = new HashSet<>();
    private String name;
    private String value;
    private String domain;
    private String path;
    private SameSite sameSite;
    private long maxAgeSeconds = Integer.MIN_VALUE;
    private boolean secure;
    private boolean httpOnly;
    private boolean discard;

    public Cookie() {
    }

    public Cookie(Cookie cookie) {
        ports.addAll(cookie.ports);
        name = cookie.name;
        value = cookie.value;
        domain = cookie.domain;
        path = cookie.path;
        sameSite = cookie.sameSite;
        maxAgeSeconds = cookie.maxAgeSeconds;
        secure = cookie.secure;
        httpOnly = cookie.httpOnly;
        discard = cookie.discard;
    }

    public Cookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Cookie setName(String name) {
        this.name = name;
        return this;
    }

    public String getValue() {
        return value;
    }

    public Cookie setValue(String value) {
        this.value = value;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public Cookie setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getPath() {
        return path;
    }

    public Cookie setPath(String path) {
        this.path = path;
        return this;
    }

    public SameSite getSameSite() {
        return sameSite;
    }

    public Cookie setSameSite(SameSite sameSite) {
        this.sameSite = sameSite;
        return this;
    }

    public int getMaxAge(TimeUnit unit) {
        return (int)unit.convert(maxAgeSeconds, TimeUnit.SECONDS);
    }

    public Cookie setMaxAge(int maxAge, TimeUnit unit) {
        this.maxAgeSeconds = maxAge >= 0 ? unit.toSeconds(maxAge) : Integer.MIN_VALUE;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public Cookie setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public Cookie setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cookie cookie = (Cookie) o;
        return maxAgeSeconds == cookie.maxAgeSeconds &&
                secure == cookie.secure &&
                httpOnly == cookie.httpOnly &&
                discard == cookie.discard &&
                sameSite == cookie.sameSite &&
                Objects.equals(ports, cookie.ports) &&
                Objects.equals(name, cookie.name) &&
                Objects.equals(value, cookie.value) &&
                Objects.equals(domain, cookie.domain) &&
                Objects.equals(path, cookie.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ports, name, value, domain, path, sameSite, maxAgeSeconds, secure, httpOnly, discard);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append(name).append("=").append(value);
        return ret.toString();
    }
    // NOTE cookie encoding and decoding:
    //      The implementation uses Jetty for server-side (encoding of Set-Cookie and decoding of Cookie header),
    //      and java.net.HttpCookie for client-side (encoding of Cookie and decoding of Set-Cookie header).
    //
    // Implementation is RFC-6265 compliant.

    public static String toCookieHeader(Iterable<? extends Cookie> cookies) {
        return StreamSupport.stream(cookies.spliterator(), false)
                .map(cookie -> {
                    java.net.HttpCookie httpCookie = new java.net.HttpCookie(cookie.getName(), cookie.getValue());
                    httpCookie.setDomain(cookie.getDomain());
                    httpCookie.setHttpOnly(cookie.isHttpOnly());
                    httpCookie.setMaxAge(cookie.getMaxAge(TimeUnit.SECONDS));
                    httpCookie.setPath(cookie.getPath());
                    httpCookie.setSecure(cookie.isSecure());
                    httpCookie.setVersion(0);
                    return httpCookie.toString();
                })
                .collect(Collectors.joining(";"));
    }

    public static List<Cookie> fromCookieHeader(String headerVal) {
        Cookies cookieCutter = new Cookies();
        cookieCutter.addCookieField(headerVal);
        return Arrays.stream(cookieCutter.getCookies())
                .map(servletCookie -> {
                    Cookie cookie = new Cookie();
                    cookie.setName(servletCookie.getName());
                    cookie.setValue(servletCookie.getValue());
                    cookie.setPath(servletCookie.getPath());
                    cookie.setDomain(servletCookie.getDomain());
                    cookie.setMaxAge(servletCookie.getMaxAge(), TimeUnit.SECONDS);
                    cookie.setSecure(servletCookie.getSecure());
                    cookie.setHttpOnly(servletCookie.isHttpOnly());
                    return cookie;
                })
                .toList();
    }

    public static List<String> toSetCookieHeaders(Iterable<? extends Cookie> cookies) {
        return StreamSupport.stream(cookies.spliterator(), false)
                .map(cookie ->
                             new org.eclipse.jetty.http.HttpCookie(
                                     cookie.getName(),
                                     cookie.getValue(),
                                     cookie.getDomain(),
                                     cookie.getPath(),
                                     cookie.getMaxAge(TimeUnit.SECONDS),
                                     cookie.isHttpOnly(),
                                     cookie.isSecure(),
                                     null, /* comment */
                                     0, /* version */
                                     Optional.ofNullable(cookie.getSameSite()).map(SameSite::jettySameSite).orElse(null)
                             ).getRFC6265SetCookie())
                .toList();
    }

    public static Cookie fromSetCookieHeader(String headerVal) {
        return java.net.HttpCookie.parse(headerVal).stream()
                .map(httpCookie -> {
                    Cookie cookie = new Cookie();
                    cookie.setName(httpCookie.getName());
                    cookie.setValue(httpCookie.getValue());
                    cookie.setDomain(httpCookie.getDomain());
                    cookie.setHttpOnly(httpCookie.isHttpOnly());
                    cookie.setMaxAge((int) httpCookie.getMaxAge(), TimeUnit.SECONDS);
                    cookie.setPath(httpCookie.getPath());
                    cookie.setSecure(httpCookie.getSecure());
                    return cookie;
                })
                .findFirst().get();
    }

    public enum SameSite {
        NONE, STRICT, LAX;

        HttpCookie.SameSite jettySameSite() {
            return HttpCookie.SameSite.valueOf(name());
        }

        static SameSite fromJettySameSite(HttpCookie.SameSite jettySameSite) {
            return valueOf(jettySameSite.name());
        }
    }
}
