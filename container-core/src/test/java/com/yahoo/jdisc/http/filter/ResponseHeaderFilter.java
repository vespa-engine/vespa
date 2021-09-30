// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;

/**
 * @author Simon Thoresen Hult
 */
public class ResponseHeaderFilter extends AbstractResource implements ResponseFilter {

    private final String key;
    private final String val;

    public ResponseHeaderFilter(String key, String val) {
        this.key = key;
        this.val = val;
    }

    @Override
    public void filter(Response response, Request request) {
        response.headers().add(key, val);
    }
}
