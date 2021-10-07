// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.google.common.collect.Iterables;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.servlet.ServletResponse;

import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.List;

/**
 * Servlet implementation for JDisc filter responses.
 */
class ServletFilterResponse extends DiscFilterResponse {

    private final ServletResponse parent;

    public ServletFilterResponse(ServletResponse parent) {
        super(parent);
        this.parent = parent;
    }

    ServletResponse getServletResponse() {
        return parent;
    }

    public void setStatus(int status) {
        parent.setStatus(status);
    }

    @Override
    public void setHeader(String name, String value) {
        parent.setHeader(name, value);
    }

    @Override
    public void removeHeaders(String name) {
        HttpServletResponse parentResponse = parent.getResponse();
        if (parentResponse instanceof org.eclipse.jetty.server.Response) {
            org.eclipse.jetty.server.Response jettyResponse = (org.eclipse.jetty.server.Response)parentResponse;
            jettyResponse.getHttpFields().remove(name);
        } else {
            throw new UnsupportedOperationException(
                    "Cannot remove headers for response of type " + parentResponse.getClass().getName());
        }
    }

    // Why have a setHeaders that takes a single string?
    @Override
    public void setHeaders(String name, String value) {
        parent.setHeader(name, value);
    }

    @Override
    public void setHeaders(String name, List<String> values) {
        for (String value : values)
            parent.addHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        parent.addHeader(name, value);
    }

    @Override
    public String getHeader(String name) {
        Collection<String> headers = parent.getHeaders(name);
        return headers.isEmpty()
                ? null
                : Iterables.getLast(headers);
    }

    @Override
    public void setCookies(List<Cookie> cookies) {
        removeHeaders(HttpHeaders.Names.SET_COOKIE);
        List<String> setCookieHeaders = Cookie.toSetCookieHeaders(cookies);
        setCookieHeaders.forEach(cookie -> addHeader(HttpHeaders.Names.SET_COOKIE, cookie));
    }
}
