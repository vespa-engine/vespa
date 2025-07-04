// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.vespa.http.server.Headers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * Abstraction around buffered/streaming writing to a {@link ResponseHandler}.
 */
class BufferedContentChannelResponseWriter implements ResponseWriter {

    private static final Logger log = Logger.getLogger(BufferedContentChannelResponseWriter.class.getName());

    private static final CompletionHandler FAILURE_LOGGING_COMPLETION_HANDLER = new CompletionHandler() {
        @Override public void completed() { }
        @Override public void failed(Throwable t) {
            log.log(FINE, "Exception writing or closing response data", t);
        }
    };

    private final BufferedContentChannel buffer = new BufferedContentChannel();
    private final ResponseHandler handler;
    private final Object lock = new Object();
    private ContentChannel channel;

    public BufferedContentChannelResponseWriter(ResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void commit(int status, String contentType, boolean fullyApplied) throws IOException {
        Response response = new Response(status);
        if (contentType != null) {
            response.headers().add("Content-Type", List.of(contentType));
        }
        if (!fullyApplied) {
            response.headers().add(Headers.IGNORED_FIELDS, "true");
        }
        synchronized (lock) {
            if (channel != null) {
                throw new IllegalStateException("commit called multiple times on same response writer");
            }
            try {
                channel = handler.handleResponse(response);
                buffer.connectTo(channel);
            } catch (RuntimeException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public void write(ByteBuffer buf, CompletionHandler completionHandlerOrNull) {
        CompletionHandler handler = completionHandlerOrNull;
        if (handler == null) {
            handler = FAILURE_LOGGING_COMPLETION_HANDLER;
        }
        buffer.write(buf, handler);
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            try {
                if (channel == null) {
                    log.log(WARNING, "Close called before response was committed, in " + getClass().getName());
                    commit(Response.Status.INTERNAL_SERVER_ERROR, null, true);
                }
            } finally {
                if (channel != null) {
                    channel.close(FAILURE_LOGGING_COMPLETION_HANDLER);
                }
            }
        }
    }

}
