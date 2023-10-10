// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.AbstractClientProvider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Christian Andersen
 */
public class MockClient extends AbstractClientProvider {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        counter.incrementAndGet();
        final ContentChannel responseContentChannel = handler.handleResponse(new Response(200));
        responseContentChannel.close(NOOP_COMPLETION_HANDLER);
        return null;
    }

    public int getCounter() {
        return counter.get();
    }

    private static final CompletionHandler NOOP_COMPLETION_HANDLER = new CompletionHandler() {
        @Override
        public void completed() {
            // Ignored
        }

        @Override
        public void failed(Throwable t) {
            // Ignored
        }
    };

}
