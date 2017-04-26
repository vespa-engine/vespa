// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
@SuppressWarnings("deprecation")
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

    public String getComment() {
        return comment;
    }

    public Cookie setComment(String comment) {
        this.comment = comment;
        return this;
    }

    public String getCommentURL() {
        return getCommentUrl();
    }

    public Cookie setCommentURL(String commentUrl) {
        return setCommentUrl(commentUrl);
    }

    public String getCommentUrl() {
        return commentUrl;
    }

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

    public int getVersion() {
        return version;
    }

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

    public boolean isDiscard() {
        return discard;
    }

    public Cookie setDiscard(boolean discard) {
        this.discard = discard;
        return this;
    }

    public Set<Integer> ports() {
        return ports;
    }

    @Override
    public int hashCode() {
        return ports.hashCode() + hashCode(name) + hashCode(value) + hashCode(domain) + hashCode(path) +
               hashCode(comment) + hashCode(commentUrl) + Long.valueOf(maxAgeMillis).hashCode() +
               Integer.valueOf(version).hashCode() + Boolean.valueOf(secure).hashCode() +
               Boolean.valueOf(httpOnly).hashCode() + Boolean.valueOf(discard).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Cookie)) {
            return false;
        }
        Cookie rhs = (Cookie)obj;
        if (!ports.equals(rhs.ports)) {
            return false;
        }
        if (!equals(name, rhs.name)) {
            return false;
        }
        if (!equals(value, rhs.value)) {
            return false;
        }
        if (!equals(domain, rhs.domain)) {
            return false;
        }
        if (!equals(path, rhs.path)) {
            return false;
        }
        if (!equals(comment, rhs.comment)) {
            return false;
        }
        if (!equals(commentUrl, rhs.commentUrl)) {
            return false;
        }
        if (maxAgeMillis != rhs.maxAgeMillis) {
            return false;
        }
        if (version != rhs.version) {
            return false;
        }
        if (secure != rhs.secure) {
            return false;
        }
        if (httpOnly != rhs.httpOnly) {
            return false;
        }
        if (discard != rhs.discard) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append(name).append("=").append(value);
        return ret.toString();
    }

    public static String toCookieHeader(Iterable<? extends Cookie> cookies) {
        return encodeCookies(cookies, false);
    }

    public static List<Cookie> fromCookieHeader(String headerVal) {
        if (headerVal == null) return Collections.emptyList();
        return decodeCookies(headerVal);
    }

    public static String toSetCookieHeader(Iterable<? extends Cookie> cookies) {
        return encodeCookies(cookies, true);
    }

    public static List<Cookie> fromSetCookieHeader(String headerVal) {
        if (headerVal == null) return Collections.emptyList();
        return decodeCookies(headerVal);
    }

    private static String encodeCookies(Iterable<? extends Cookie> cookies, boolean server) {
        org.jboss.netty.handler.codec.http.CookieEncoder encoder =
                new org.jboss.netty.handler.codec.http.CookieEncoder(server);
        for (Cookie cookie : cookies) {
            org.jboss.netty.handler.codec.http.Cookie nettyCookie =
                    new org.jboss.netty.handler.codec.http.DefaultCookie(String.valueOf(cookie.getName()), String.valueOf(cookie.getValue()));
            nettyCookie.setComment(cookie.getComment());
            nettyCookie.setCommentUrl(cookie.getCommentUrl());
            nettyCookie.setDiscard(cookie.isDiscard());
            nettyCookie.setDomain(cookie.getDomain());
            nettyCookie.setHttpOnly(cookie.isHttpOnly());
            nettyCookie.setMaxAge(cookie.getMaxAge(TimeUnit.SECONDS));
            nettyCookie.setPath(cookie.getPath());
            nettyCookie.setSecure(cookie.isSecure());
            nettyCookie.setVersion(cookie.getVersion());
            nettyCookie.setPorts(cookie.ports());
            encoder.addCookie(nettyCookie);
        }
        return encoder.encode();
    }

    private static List<Cookie> decodeCookies(String str) {
        org.jboss.netty.handler.codec.http.CookieDecoder decoder =
                new org.jboss.netty.handler.codec.http.CookieDecoder();
        List<Cookie> ret = new LinkedList<>();
        for (org.jboss.netty.handler.codec.http.Cookie nettyCookie : decoder.decode(str)) {
            Cookie cookie = new Cookie();
            cookie.setName(nettyCookie.getName());
            cookie.setValue(nettyCookie.getValue());
            cookie.setComment(nettyCookie.getComment());
            cookie.setCommentUrl(nettyCookie.getCommentUrl());
            cookie.setDiscard(nettyCookie.isDiscard());
            cookie.setDomain(nettyCookie.getDomain());
            cookie.setHttpOnly(nettyCookie.isHttpOnly());
            cookie.setMaxAge(nettyCookie.getMaxAge(), TimeUnit.SECONDS);
            cookie.setPath(nettyCookie.getPath());
            cookie.setSecure(nettyCookie.isSecure());
            cookie.setVersion(nettyCookie.getVersion());
            cookie.ports().addAll(nettyCookie.getPorts());
            ret.add(cookie);
        }
        return ret;
    }

    private static int hashCode(Object obj) {
        if (obj == null) {
            return 0;
        }
        return obj.hashCode();
    }

    private static boolean equals(Object lhs, Object rhs) {
        if (lhs == null || rhs == null) {
            return lhs == rhs;
        }
        return lhs.equals(rhs);
    }
}
