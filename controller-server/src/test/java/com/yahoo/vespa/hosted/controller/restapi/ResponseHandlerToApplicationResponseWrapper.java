// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link ResponseHandler} that caches the response content and
 * converts a {@link Response} to {@link com.yahoo.application.container.handler.Response}.
 *
 * @author bjorncs
 */
public class ResponseHandlerToApplicationResponseWrapper implements ResponseHandler {

    private Response response;
    private SimpleContentChannel contentChannel;

    @Override
    public ContentChannel handleResponse(Response response) {
        this.response = response;
        SimpleContentChannel contentChannel = new SimpleContentChannel();
        this.contentChannel = contentChannel;
        return contentChannel;
    }

    public Optional<com.yahoo.application.container.handler.Response> toResponse() {
        return Optional.ofNullable(this.response)
                .map(r -> {
                    byte[] bytes = contentChannel.toByteArray();
                    return new com.yahoo.application.container.handler.Response(response.getStatus(), bytes);
                });
    }

    private class SimpleContentChannel implements ContentChannel {

        private final Queue<ByteBuffer> buffers = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            buffers.add(buf);
            handler.completed();
        }

        @Override
        public void close(CompletionHandler handler) {
            handler.completed();
            if (closed.getAndSet(true)) {
                throw new IllegalStateException("Already closed");
            }
        }

        byte[] toByteArray() {
            if (!closed.get()) {
                throw new IllegalStateException("Content channel not closed yet");
            }
            int totalSize = 0;
            for (ByteBuffer responseBuffer : buffers) {
                totalSize += responseBuffer.remaining();
            }
            ByteBuffer totalBuffer = ByteBuffer.allocate(totalSize);
            for (ByteBuffer responseBuffer : buffers) {
                totalBuffer.put(responseBuffer);
            }
            return totalBuffer.array();
        }
    }

}
