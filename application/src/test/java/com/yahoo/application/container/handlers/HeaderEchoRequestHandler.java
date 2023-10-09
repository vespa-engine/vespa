// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handlers;

import com.yahoo.jdisc.Request;
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
public class HeaderEchoRequestHandler extends AbstractRequestHandler {
    private ExecutorService responseExecutor = Executors.newSingleThreadExecutor();

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        responseExecutor.execute(new HeaderEchoTask(request, handler));
        return new HeaderEchoContentChannel();
    }


    private static class HeaderEchoTask implements Runnable {
        private final Request request;
        private final ResponseHandler handler;

        public HeaderEchoTask(Request request, ResponseHandler handler) {
            this.request = request;
            this.handler = handler;
        }

        @Override
        public void run() {
            com.yahoo.jdisc.Response response = new com.yahoo.jdisc.Response(com.yahoo.jdisc.Response.Status.OK);
            response.headers().addAll(request.headers());
            ContentChannel responseChannel = handler.handleResponse(response);
            responseChannel.close(null);
        }
    }


    private static class HeaderEchoContentChannel implements ContentChannel {
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            //we will not accept header body data
            handler.completed();
        }

        @Override
        public void close(CompletionHandler handler) {
            //we will not accept header body data
            handler.completed();
        }
    }
}
