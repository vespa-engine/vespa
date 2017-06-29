// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;

import javax.servlet.http.HttpServletRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Enumeration;

/**
 * @author Simon Thoresen Hult
 */
class HttpRequestFactory {

    public static HttpRequest newJDiscRequest(CurrentContainer container, HttpServletRequest servletRequest) {
        return HttpRequest.newServerRequest(
                container,
                getUri(servletRequest),
                HttpRequest.Method.valueOf(servletRequest.getMethod()),
                HttpRequest.Version.fromString(servletRequest.getProtocol()),
                new InetSocketAddress(servletRequest.getRemoteAddr(), servletRequest.getRemotePort()));
    }

    public static URI getUri(HttpServletRequest servletRequest) {
        String query = extraQuote(servletRequest.getQueryString());
        try {
            return URI.create(servletRequest.getRequestURL() + (query != null ? '?' + query : ""));
        } catch (IllegalArgumentException e) {
            throw new RequestException(Response.Status.BAD_REQUEST, "Query violates RFC 2396", e);
        }
    }

    public static void copyHeaders(HttpServletRequest from, HttpRequest to) {
        for (Enumeration<String> it = from.getHeaderNames(); it.hasMoreElements(); ) {
            String key = it.nextElement();
            for (Enumeration<String> value = from.getHeaders(key); value.hasMoreElements(); ) {
                to.headers().add(key, value.nextElement());
            }
        }
    }

    private static String extraQuote(String queryString) {
        // TODO: Use an URI builder
        if (queryString == null) return null;

        int toAndIncluding = -1;
        for (int i = 0; i < queryString.length(); ++i) {
            if (quote(queryString.charAt(i)) != null) {
                break;
            }
            toAndIncluding = i;
        }

        String washed;
        if (toAndIncluding != (queryString.length() - 1)) {
            StringBuilder w = new StringBuilder(queryString.substring(0, toAndIncluding + 1));
            for (int i = toAndIncluding + 1; i < queryString.length(); ++i) {
                String s = quote(queryString.charAt(i));
                if (s == null) {
                    w.append(queryString.charAt(i));
                } else {
                    w.append(s);
                }
            }
            washed = w.toString();
        } else {
            washed = queryString;
        }
        return washed;
    }

    private static String quote(char c) {
        switch(c) {
        case '\\':
            return "%5C";
        case '^':
            return "%5E";
        case '{':
            return "%7B";
        case '|':
            return "%7C";
        case '}':
            return "%7D";
        default:
            return null;
        }
    }

}
