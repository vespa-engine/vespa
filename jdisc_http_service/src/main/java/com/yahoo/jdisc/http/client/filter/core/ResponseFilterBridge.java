// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client.filter.core;

import com.ning.http.client.filter.FilterContext;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.http.client.filter.ResponseFilterContext;

import java.net.URISyntaxException;
import java.util.Collections;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class ResponseFilterBridge {

    public static ResponseFilterContext toResponseFilterContext(FilterContext<?> filterContext, Request request) {
        try {
            return new ResponseFilterContext.Builder()
                    .uri(filterContext.getRequest().getUri().toJavaNetURI())
                    .statusCode(filterContext.getResponseStatus().getStatusCode())
                    .headers(filterContext.getResponseHeaders().getHeaders())
                    .requestContext(request == null ? Collections.<String, Object>emptyMap() : request.context())
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Bad URI", e);
        }
    }

}
