// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.servlet;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpRequest;

import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Common interface for JDisc and servlet http requests.
 */
public interface ServletOrJdiscHttpRequest {

    void copyHeaders(HeaderFields target);

    Map<String, List<String>> parameters();

    URI getUri();

    HttpRequest.Version getVersion();

    String getRemoteHostAddress();
    String getRemoteHostName();
    int getRemotePort();

    void setRemoteAddress(SocketAddress remoteAddress);

    Map<String, Object> context();

    List<Cookie> decodeCookieHeader();

    void encodeCookieHeader(List<Cookie> cookies);

    long getConnectedAt(TimeUnit unit);
}
