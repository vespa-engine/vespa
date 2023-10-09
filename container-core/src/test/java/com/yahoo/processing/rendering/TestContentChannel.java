// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.rendering;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
* @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
* @since 5.1.9
*/
class TestContentChannel implements ContentChannel {
    private final List<ByteBuffer> buffers = new ArrayList<>();
    private boolean closed = false;

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        buffers.add(buf);
        if (handler != null) {
            handler.completed();
        }
    }

    @Override
    public void close(CompletionHandler handler) {
        closed = true;
        if (handler != null) {
            handler.completed();
        }
    }

    public List<ByteBuffer> getBuffers() {
        return buffers;
    }

    public boolean isClosed() {
        return closed;
    }
}
