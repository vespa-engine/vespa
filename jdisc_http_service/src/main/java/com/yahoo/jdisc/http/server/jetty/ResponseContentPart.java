// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.handler.CompletionHandler;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author tonytv
 * @author simon
 */
class ResponseContentPart {
    private static final Logger log = Logger.getLogger(ResponseContentPart.class.getName());

    final ByteBuffer buf;
    final CompletionHandler handler;

    ResponseContentPart(final ByteBuffer buf, final CompletionHandler handler) {
        this.buf = (buf != null) ? buf : ByteBuffer.allocate(0);
        this.handler = (handler != null) ? handler: DEFAULT_COMPLETION_HANDLER;
    }

    private static final CompletionHandler DEFAULT_COMPLETION_HANDLER = new CompletionHandler() {
        @Override
        public void completed() {
            log.log(Level.FINE, "DefaultCompletionHandler: Operation completed");
        }

        @Override
        public void failed(Throwable t) {
            log.log(Level.FINE, "DefaultCompletionHandler: Operation failed", t);
        }
    };
}
