// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.eclipse.jetty.server.CookieCutter;
import org.eclipse.jetty.server.Response;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A RFC 6265 compliant cookie.
 *
 * Note: RFC 2109 and RFC 2965 is no longer supported. All fields that are not part of RFC 6265 are deprecated.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @author bjorncs
 */
public class Cookie {

    private final static Logger log = Logger.getLogger(Cookie.class.getName());

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
    // NOTE cookie encoding and decoding:
    //      The implementation uses Jetty for server-side (encoding of Set-Cookie and decoding of Cookie header),
    //      and java.net.HttpCookie for client-side (encoding of Cookie and decoding of Set-Cookie header).
    //
    // Implementation is RFC-6265 compliant.

    public static String toCookieHeader(Iterable<? extends Cookie> cookies) {
        return encodeCookieHeaderValue(toCookieList(cookies));
    }

    public static List<Cookie> fromCookieHeader(String headerVal) {
        return decodeCookieHeaderValue(headerVal);
    }

    /**
     * @deprecated Use {@link #toSetCookieHeaderAll(Iterable)} instead.
     */
    @Deprecated
    public static String toSetCookieHeader(Iterable<? extends Cookie> cookies) {
        List<String> encodedCookies = encodeSetCookieHeaderValue(cookies);
        return encodedCookies.isEmpty() ? null : encodedCookies.get(0);
    }

    // TODO Rename to toSetCookieHeader for Vespa 7
    public static List<String> toSetCookieHeaderAll(Iterable<? extends Cookie> cookies) {
        return encodeSetCookieHeaderValue(cookies);
    }

    // TODO Change return type to Cookie for Vespa 7
    public static List<Cookie> fromSetCookieHeader(String headerVal) {
        return decodeSetCookieHeaderValue(headerVal);
    }


    private static List<String> encodeSetCookieHeaderValue(Iterable<? extends Cookie> cookies) {
        // Ugly, bot Jetty does not provide a dedicated cookie parser (will be included in Jetty 10)
        Response response = new Response(null, null);
        for (Cookie cookie : cookies) {
            response.addSetRFC6265Cookie(
                    cookie.getName(),
                    cookie.getValue(),
                    cookie.getDomain(),
                    cookie.getPath(),
                    cookie.getMaxAge(TimeUnit.SECONDS),
                    cookie.isSecure(),
                    cookie.isHttpOnly());
        }
        return new ArrayList<>(response.getHeaders("Set-Cookie"));
    }

    private static String encodeCookieHeaderValue(List<Cookie> cookies) {
        return cookies.stream()
                .map(cookie -> {
                    HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
                    httpCookie.setComment(cookie.getComment());
                    httpCookie.setCommentURL(cookie.getCommentURL());
                    httpCookie.setDiscard(cookie.isDiscard());
                    httpCookie.setDomain(cookie.getDomain());
                    httpCookie.setHttpOnly(cookie.isHttpOnly());
                    httpCookie.setMaxAge(cookie.getMaxAge(TimeUnit.SECONDS));
                    httpCookie.setPath(cookie.getPath());
                    httpCookie.setSecure(cookie.isSecure());
                    httpCookie.setVersion(cookie.getVersion());
                    String portList = cookie.ports().stream()
                            .map(Number::toString)
                            .collect(Collectors.joining(","));
                    httpCookie.setPortlist(portList);
                    return httpCookie.toString();
                })
                .collect(Collectors.joining(";"));
    }

    private static List<Cookie> decodeSetCookieHeaderValue(String headerVal) {

        return HttpCookie.parse(headerVal).stream()
                .map(httpCookie -> {
                    Cookie cookie = new Cookie();
                    cookie.setName(httpCookie.getName());
                    cookie.setValue(httpCookie.getValue());
                    cookie.setComment(httpCookie.getComment());
                    cookie.setCommentUrl(httpCookie.getCommentURL());
                    cookie.setDiscard(httpCookie.getDiscard());
                    cookie.setDomain(httpCookie.getDomain());
                    cookie.setHttpOnly(httpCookie.isHttpOnly());
                    int maxAge = (int) httpCookie.getMaxAge();
                    cookie.setMaxAge(maxAge != -1 ? maxAge : Integer.MIN_VALUE, TimeUnit.SECONDS);
                    cookie.setPath(httpCookie.getPath());
                    cookie.setSecure(httpCookie.getSecure());
                    cookie.setVersion(httpCookie.getVersion());
                    cookie.ports().addAll(parsePortList(httpCookie.getPortlist()));
                    return cookie;
                })
                .collect(Collectors.toList());
    }

    private static List<Cookie> decodeCookieHeaderValue(String headerVal) {
        CookieCutter cookieCutter = new CookieCutter();
        cookieCutter.addCookieField(headerVal);
        return Arrays.stream(cookieCutter.getCookies())
                .map(servletCookie -> {
                    Cookie cookie = new Cookie();
                    cookie.setName(servletCookie.getName());
                    cookie.setValue(servletCookie.getValue());
                    cookie.setComment(servletCookie.getComment());
                    cookie.setPath(servletCookie.getPath());
                    cookie.setDomain(servletCookie.getDomain());
                    int maxAge = servletCookie.getMaxAge();
                    cookie.setMaxAge(maxAge != -1 ? maxAge : Integer.MIN_VALUE, TimeUnit.SECONDS);
                    cookie.setSecure(servletCookie.getSecure());
                    cookie.setVersion(servletCookie.getVersion());
                    cookie.setHttpOnly(servletCookie.isHttpOnly());
                    return cookie;
                })
                .collect(Collectors.toList());
    }

    private static List<Integer> parsePortList(String rawPortList) {
        if (rawPortList == null) return Collections.emptyList();

        List<Integer> ports = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(rawPortList, ",");
        while (tokenizer.hasMoreTokens()) {
            String rawPort = tokenizer.nextToken().trim();
            if (!rawPort.isEmpty()) {
                try {
                    ports.add(Integer.parseInt(rawPort));
                } catch (NumberFormatException e) {
                    log.log(Level.FINE, "Unable to parse port: " + rawPort, e);
                }
            }
        }
        return ports;
    }

    private static ArrayList<Cookie> toCookieList(Iterable<? extends Cookie> cookies) {
        ArrayList<Cookie> cookieList = new ArrayList<>();
        cookies.forEach(cookieList::add);
        return cookieList;
    }
}
