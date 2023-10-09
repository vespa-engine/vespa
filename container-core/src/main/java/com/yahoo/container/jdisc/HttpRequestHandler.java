// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc;

import com.yahoo.jdisc.handler.RequestHandler;

/**
 * Extends a request handler with a http specific
 *
 * @author mortent
 */
public interface HttpRequestHandler extends RequestHandler {

    /**
     * @return handler specification
     */
    default RequestHandlerSpec requestHandlerSpec() {
        return RequestHandlerSpec.DEFAULT_INSTANCE;
    }
}
