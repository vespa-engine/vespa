// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;

import java.util.concurrent.Executor;

/**
 * Dummy handler for paths that should be handled in a request filter.
 *
 * @author freva
 */
public class FilterBackingRequestHandler extends ThreadedHttpRequestHandler {

    @Inject
    public FilterBackingRequestHandler(Executor executor) {
        super(executor);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        log.warning("Expected " + request.getMethod() + " request to " + request.getUri().getRawPath() +
                " have been handled by a request filter");
        return ErrorResponse.internalServerError("No handler for this path");
    }

}
