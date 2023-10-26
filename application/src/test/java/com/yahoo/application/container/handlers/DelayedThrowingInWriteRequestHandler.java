// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handlers;

import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Einar M R Rosenvinge
 */
public class DelayedThrowingInWriteRequestHandler extends AbstractRequestHandler {
    private ExecutorService responseExecutor = Executors.newSingleThreadExecutor();

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        responseExecutor.execute(new DelayedThrowingInWriteTask(handler));
        return new DelayedThrowingInWriteContentChannel();
    }


    private static class DelayedThrowingInWriteTask implements Runnable {
        private final ResponseHandler handler;

        public DelayedThrowingInWriteTask(ResponseHandler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            ContentChannel responseChannel = handler.handleResponse(
                    new com.yahoo.jdisc.Response(com.yahoo.jdisc.Response.Status.OK));
            responseChannel.close(null);
        }
    }


    private static class DelayedThrowingInWriteContentChannel implements ContentChannel {
        private List<CompletionHandler> writeCompletionHandlers = new ArrayList<>();
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            writeCompletionHandlers.add(handler);
        }

        @Override
        public void close(CompletionHandler handler) {
            for (CompletionHandler writeCompletionHandler : writeCompletionHandlers) {
                writeCompletionHandler.failed(new DelayedWriteException());
            }
            handler.completed();
        }
    }
}
