// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.jboss.netty.handler.codec.http.cookie.ClientCookieDecoder;
import org.jboss.netty.handler.codec.http.cookie.ClientCookieEncoder;
import org.jboss.netty.handler.codec.http.cookie.DefaultCookie;
import org.jboss.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.jboss.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A RFC 6265 compliant cookie.
 *
 * Note: RFC 2109 and RFC 2965 is no longer supported. All fields that are not part of RFC 6265 are deprecated.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @author bjorncs
 */
public class Cookie {

    private final Set<Integer> ports = new HashSet<>();
    private String name;
    private String value;
    private String domain;
    private String path;
    private String comment;
    private String commentUrl;
    private long maxAgeMillis = TimeUnit.SECONDS.toMillis(Integer.MIN_VALUE);
    private int version;
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
        comment = cookie.comment;
        commentUrl = cookie.commentUrl;
        maxAgeMillis = cookie.maxAgeMillis;
        version = cookie.version;
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

    @Deprecated
    public String getComment() {
        return comment;
    }

    @Deprecated
    public Cookie setComment(String comment) {
        this.comment = comment;
        return this;
    }

    @Deprecated
    public String getCommentURL() {
        return getCommentUrl();
    }

    @Deprecated
    public Cookie setCommentURL(String commentUrl) {
        return setCommentUrl(commentUrl);
    }

    @Deprecated
    public String getCommentUrl() {
        return commentUrl;
    }

    @Deprecated
    public Cookie setCommentUrl(String commentUrl) {
        this.commentUrl = commentUrl;
        return this;
    }

    public int getMaxAge(TimeUnit unit) {
        return (int)unit.convert(maxAgeMillis, TimeUnit.MILLISECONDS);
    }

    public Cookie setMaxAge(int maxAge, TimeUnit unit) {
        this.maxAgeMillis = unit.toMillis(maxAge);
        return this;
    }

    @Deprecated
    public int getVersion() {
        return version;
    }

    @Deprecated
    public Cookie setVersion(int version) {
        this.version = version;
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

    @Deprecated
    public boolean isDiscard() {
        return discard;
    }

    @Deprecated
    public Cookie setDiscard(boolean discard) {
        this.discard = discard;
        return this;
    }

    @Deprecated
    public Set<Integer> ports() {
        return ports;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cookie cookie = (Cookie) o;
        return maxAgeMillis == cookie.maxAgeMillis &&
                version == cookie.version &&
                secure == cookie.secure &&
                httpOnly == cookie.httpOnly &&
                discard == cookie.discard &&
                Objects.equals(ports, cookie.ports) &&
                Objects.equals(name, cookie.name) &&
                Objects.equals(value, cookie.value) &&
                Objects.equals(domain, cookie.domain) &&
                Objects.equals(path, cookie.path) &&
                Objects.equals(comment, cookie.comment) &&
                Objects.equals(commentUrl, cookie.commentUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ports, name, value, domain, path, comment, commentUrl, maxAgeMillis, version, secure, httpOnly, discard);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append(name).append("=").append(value);
        return ret.toString();
    }

    public static String toCookieHeader(Iterable<? extends Cookie> cookies) {
        ClientCookieEncoder encoder = ClientCookieEncoder.STRICT;
        List<org.jboss.netty.handler.codec.http.cookie.Cookie> nettyCookies =
                StreamSupport.stream(cookies.spliterator(), false)
                        // NOTE: Only name and value is included in Cookie header as of RFC-6265
                        .map(cookie -> new DefaultCookie(cookie.getName(), cookie.getValue()))
                        .collect(Collectors.toList());
        return encoder.encode(nettyCookies);
    }

    public static List<Cookie> fromCookieHeader(String headerVal) {
        if (headerVal == null) return Collections.emptyList();

        ServerCookieDecoder decoder = ServerCookieDecoder.STRICT;
        Set<org.jboss.netty.handler.codec.http.cookie.Cookie> nettyCookies = decoder.decode(headerVal);
        return nettyCookies.stream()
                // NOTE: Only name and value is included in Cookie header as of RFC-6265
                .map(nettyCookie -> new Cookie(nettyCookie.name(), nettyCookie.value()))
                .collect(Collectors.toList());
    }

    /**
     * @deprecated Use {@link #toSetCookieHeaderAll(Iterable)} instead.
     */
    @Deprecated
    public static String toSetCookieHeader(Iterable<? extends Cookie> cookies) {
        List<String> encodedCookies = toSetCookieHeaderAll(cookies);
        return encodedCookies.isEmpty() ? null : encodedCookies.get(0);
    }

    // TODO Rename to toSetCookieHeader for Vespa 7
    public static List<String> toSetCookieHeaderAll(Iterable<? extends Cookie> cookies) {
        ServerCookieEncoder encoder = ServerCookieEncoder.STRICT;
        List<org.jboss.netty.handler.codec.http.cookie.Cookie> nettyCookies =
                StreamSupport.stream(cookies.spliterator(), false)
                        .map(cookie -> {
                            org.jboss.netty.handler.codec.http.cookie.Cookie nettyCookie
                                    = new DefaultCookie(cookie.getName(), cookie.getValue());
                            nettyCookie.setPath(cookie.getPath());
                            nettyCookie.setMaxAge(cookie.getMaxAge(TimeUnit.SECONDS));
                            nettyCookie.setSecure(cookie.isSecure());
                            nettyCookie.setHttpOnly(cookie.isHttpOnly());
                            nettyCookie.setDomain(cookie.getDomain());
                            return nettyCookie;
                        })
                        .collect(Collectors.toList());
        return encoder.encode(nettyCookies);
    }

    // TODO Change return type to Cookie for Vespa 7
    public static List<Cookie> fromSetCookieHeader(String headerVal) {
        if (headerVal == null) return Collections.emptyList();

        ClientCookieDecoder encoder = ClientCookieDecoder.STRICT;
        org.jboss.netty.handler.codec.http.cookie.Cookie nettyCookie = encoder.decode(headerVal);
        return Collections.singletonList(new Cookie(nettyCookie.name(), nettyCookie.value())
                .setHttpOnly(nettyCookie.isHttpOnly())
                .setSecure(nettyCookie.isSecure())
                .setMaxAge(nettyCookie.maxAge(), TimeUnit.SECONDS)
                .setPath(nettyCookie.path())
                .setDomain(nettyCookie.domain()));
    }
}
