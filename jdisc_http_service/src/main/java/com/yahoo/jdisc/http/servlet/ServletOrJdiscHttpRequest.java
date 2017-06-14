// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.servlet;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpRequest;

import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Common interface for JDisc and servlet http requests.
 */
public interface ServletOrJdiscHttpRequest {

    public void copyHeaders(HeaderFields target);

    public Map<String, List<String>> parameters();

    public URI getUri();

    public HttpRequest.Version getVersion();

    public String getRemoteHostAddress();
    public String getRemoteHostName();
    public int getRemotePort();

    public void setRemoteAddress(SocketAddress remoteAddress);

    public Map<String, Object> context();

    public List<Cookie> decodeCookieHeader();

    public void encodeCookieHeader(List<Cookie> cookies);
}
