// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.ClientProvider;

/**
 * @author Simon Thoresen Hult
 */
public final class NonWorkingClientProvider extends NoopSharedResource implements ClientProvider {

    @Override
    public void start() {

    }

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void handleTimeout(Request request, ResponseHandler handler) {
        throw new UnsupportedOperationException();
    }
}
