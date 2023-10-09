// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handlers;

import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
* @author Einar M R Rosenvinge
*/
public class ThrowingInWriteRequestHandler extends AbstractRequestHandler {

    private ExecutorService responseExecutor = Executors.newSingleThreadExecutor();

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        responseExecutor.execute(new ThrowingInWriteTask(handler));
        return new ThrowingInWriteContentChannel();
    }


    private static class ThrowingInWriteTask implements Runnable {
        private final ResponseHandler handler;

        public ThrowingInWriteTask(ResponseHandler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            ContentChannel responseChannel = handler.handleResponse(
                    new com.yahoo.jdisc.Response(com.yahoo.jdisc.Response.Status.OK));
            responseChannel.close(null);
        }
    }


    private static class ThrowingInWriteContentChannel implements ContentChannel {
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            throw new WriteException();
        }

        @Override
        public void close(CompletionHandler handler) {
            handler.completed();
        }
    }

}
