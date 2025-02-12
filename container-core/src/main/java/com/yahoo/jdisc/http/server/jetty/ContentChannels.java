// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import java.nio.ByteBuffer;

/**
 * @author bjorncs
 */
class ContentChannels {

    private ContentChannels() {}

    private static final ContentChannel NOOP_CONTENT_CHANNEL = new ContentChannel() {
        @Override public void write(ByteBuffer buf, CompletionHandler h) { h.completed();}
        @Override public void close(CompletionHandler h) { h.completed(); }
    };

    static ContentChannel noop() { return NOOP_CONTENT_CHANNEL; }

}
