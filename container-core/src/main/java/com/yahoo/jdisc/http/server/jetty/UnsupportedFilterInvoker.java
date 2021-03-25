// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * @author Tony Vaagenes
 */
public class UnsupportedFilterInvoker implements FilterInvoker {
    @Override
    public HttpServletRequest invokeRequestFilterChain(RequestFilter requestFilterChain,
                                                       URI uri,
                                                       HttpServletRequest httpRequest,
                                                       ResponseHandler responseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void invokeResponseFilterChain(
            ResponseFilter responseFilterChain,
            URI uri,
            HttpServletRequest request,
            HttpServletResponse response) {
        throw new UnsupportedOperationException();
    }
}
