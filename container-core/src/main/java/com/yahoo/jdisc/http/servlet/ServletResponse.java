// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.servlet;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpHeaders;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * JDisc wrapper to use a {@link javax.servlet.http.HttpServletResponse}
 * with JDisc security filters.
 */
public class ServletResponse extends HttpServletResponseWrapper implements ServletOrJdiscHttpResponse {

    private final HttpServletResponse response;
    private final Map<String, Object> context = new HashMap<>();

    public ServletResponse(HttpServletResponse response) {
        super(response);
        this.response = response;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    @Override
    public int getStatus() {
        return response.getStatus();
    }

    @Override
    public Map<String, Object> context() {
        return context;
    }

    @Override
    public void copyHeaders(HeaderFields target) {
        response.getHeaderNames().forEach( header ->
            target.add(header, new ArrayList<>(response.getHeaders(header)))
        );
    }

    @Override
    public List<Cookie> decodeSetCookieHeader() {
        Collection<String> cookies = getHeaders(HttpHeaders.Names.SET_COOKIE);
        if (cookies == null) {
            return Collections.emptyList();
        }
        List<Cookie> ret = new LinkedList<>();
        for (String cookie : cookies) {
            ret.add(Cookie.fromSetCookieHeader(cookie));
        }
        return ret;
    }

}
