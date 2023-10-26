// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.http;

import com.yahoo.jdisc.http.HttpRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

/**
 * @author bakksjo
 */
public class AccessLogUtil {

    public static String getHttpMethod(final HttpRequest httpRequest) {
        return httpRequest.getMethod().toString();
    }

    public static URI getUri(final HttpRequest httpRequest) {
        return httpRequest.getUri();
    }

    public static String getHttpVersion(final HttpRequest httpRequest) {
        return httpRequest.getVersion().toString();
    }

    public static String getReferrerHeader(final HttpRequest httpRequest) {
        // Yes, the header name is misspelled in the standard
        return getFirstHeaderValue(httpRequest, "Referer");
    }

    public static String getUserAgentHeader(final HttpRequest httpRequest) {
        return getFirstHeaderValue(httpRequest, "User-Agent");
    }

    public static InetSocketAddress getRemoteAddress(final HttpRequest httpRequest) {
        return (InetSocketAddress) httpRequest.getRemoteAddress();
    }

    private static String getFirstHeaderValue(final HttpRequest httpRequest, final String headerName) {
        final List<String> headerValues = httpRequest.headers().get(headerName);
        return (headerValues == null || headerValues.isEmpty()) ? "" : headerValues.get(0);
    }

}
