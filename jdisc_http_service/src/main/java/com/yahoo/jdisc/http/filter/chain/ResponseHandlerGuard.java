// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.chain;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * @author Simon Thoresen Hult
 */
final class ResponseHandlerGuard implements ResponseHandler {

    private final ResponseHandler responseHandler;
    private boolean done = false;

    public ResponseHandlerGuard(ResponseHandler handler) {
        this.responseHandler = handler;
    }

    @Override
    public ContentChannel handleResponse(Response response) {
        done = true;
        return responseHandler.handleResponse(response);
    }

    public boolean isDone() {
        return done;
    }
}
