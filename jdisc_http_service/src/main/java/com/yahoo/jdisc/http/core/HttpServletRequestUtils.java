// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.core;

import org.eclipse.jetty.server.HttpConnection;

import javax.servlet.http.HttpServletRequest;

/**
 * @author bjorncs
 */
public class HttpServletRequestUtils {
    private HttpServletRequestUtils() {}

    public static HttpConnection getConnection(HttpServletRequest request) {
        return (HttpConnection)request.getAttribute("org.eclipse.jetty.server.HttpConnection");
    }

}
